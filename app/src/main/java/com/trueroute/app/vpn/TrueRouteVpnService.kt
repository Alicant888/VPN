package com.trueroute.app.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.trueroute.app.MainActivity
import com.trueroute.app.R
import com.trueroute.app.TrueRouteApp
import com.trueroute.app.data.TunnelSessionRepository
import com.trueroute.app.model.DnsMode
import com.trueroute.app.model.LogLevel
import com.trueroute.app.model.ProxyConfig
import com.trueroute.app.model.RoutingMode
import com.trueroute.app.model.TunnelPhase
import com.trueroute.app.model.TunnelStats
import com.trueroute.app.validation.ProxyConfigValidation
import com.trueroute.app.validation.ProxyConfigValidator
import hev.htproxy.TProxyService
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class TrueRouteVpnService : VpnService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nativeTunnel by lazy { TProxyService() }

    private lateinit var sessionRepository: TunnelSessionRepository

    private var tunnelInterface: ParcelFileDescriptor? = null
    private var statsJob: Job? = null
    private var nativeLogJob: Job? = null
    private var nativeRuntimeFiles: HevRuntimeFiles? = null
    private var nativeLogOffset: Long = 0L

    override fun onCreate() {
        super.onCreate()
        sessionRepository = (application as TrueRouteApp).container.tunnelSessionRepository
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> serviceScope.launch { stopTunnel(stopServiceSelf = true) }
            else -> serviceScope.launch { startTunnel() }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        serviceScope.launch { stopTunnel(stopServiceSelf = true) }
        super.onRevoke()
    }

    override fun onDestroy() {
        runBlocking {
            stopTunnel(stopServiceSelf = false)
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun startTunnel() {
        if (tunnelInterface != null) {
            return
        }

        sessionRepository.reset()
        sessionRepository.updatePhase(TunnelPhase.CONNECTING, "Checking SOCKS5 proxy")
        sessionRepository.appendLog(LogLevel.INFO, "Running SOCKS5 preflight")

        val config = when (
            val validation = ProxyConfigValidator.validate(
                (application as TrueRouteApp).container.proxyConfigRepository.readForm(),
            )
        ) {
            is ProxyConfigValidation.Valid -> validation.config
            is ProxyConfigValidation.Invalid -> {
                reportError(validation.reason)
                stopSelf()
                return
            }
        }

        val preflight = Socks5Preflight.run(config)
        if (!preflight.success) {
            reportError("Proxy preflight failed: ${preflight.message}")
            stopSelf()
            return
        }

        sessionRepository.appendLog(LogLevel.INFO, preflight.message)
        preflight.authentication?.let {
            sessionRepository.appendLog(LogLevel.INFO, "Preflight auth: $it")
        }
        preflight.udpAssociateAddress?.let {
            sessionRepository.appendLog(LogLevel.INFO, "Preflight UDP Associate: $it")
        }
        sessionRepository.updatePhase(TunnelPhase.CONNECTING, "Preparing VPN tunnel")

        val runtimeFiles = HevConfigWriter.writeConfig(cacheDir, config)
        nativeRuntimeFiles = runtimeFiles
        startNativeLogPolling(runtimeFiles.logFile)

        try {
            val vpnInterface = establishTunnel(config)
            if (vpnInterface == null) {
                reportError("Failed to establish Android VPN interface")
                stopNativeLogPolling()
                stopSelf()
                return
            }
            tunnelInterface = vpnInterface

            startForegroundWithNotification()
            sessionRepository.appendLog(LogLevel.INFO, "Starting native tunnel")
            nativeTunnel.TProxyStartService(runtimeFiles.configFile.absolutePath, vpnInterface.fd)
            delay(NATIVE_START_GRACE_PERIOD_MS)
            drainNativeLogFile(runtimeFiles.logFile)

            if (!nativeTunnel.TProxyIsRunning()) {
                reportError("Native tunnel exited early: ${nativeResultMessage(nativeTunnel.TProxyGetLastResult())}")
                stopTunnel(stopServiceSelf = true, preserveError = true)
                return
            }

            sessionRepository.appendLog(LogLevel.INFO, "SOCKS5 tunnel started with UDP Associate")
            sessionRepository.updatePhase(
                TunnelPhase.CONNECTED,
                "Connected via ${config.proxyHost}:${config.proxyPort}",
            )
            startStatsPolling()
        } catch (error: UnsatisfiedLinkError) {
            reportError("Native tunnel library is unavailable: ${error.message.orEmpty()}")
            stopTunnel(stopServiceSelf = true, preserveError = true)
        } catch (error: Exception) {
            reportError("Tunnel start failed: ${error.message.orEmpty()}")
            stopTunnel(stopServiceSelf = true, preserveError = true)
        }
    }

    private fun establishTunnel(config: ProxyConfig): ParcelFileDescriptor? {
        val builder = Builder()
            .setBlocking(false)
            .setSession(buildSessionName(config))
            .setMtu(HevConfigWriter.TUNNEL_MTU)
            .addAddress(HevConfigWriter.TUNNEL_IPV4, 32)
            .addAddress(HevConfigWriter.TUNNEL_IPV6, 128)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)

        when (config.dnsMode) {
            DnsMode.PROVIDER -> builder.addDnsServer(HevConfigWriter.MAPPED_DNS)
            DnsMode.CUSTOM -> builder.addDnsServer(config.customDns ?: DEFAULT_CUSTOM_DNS)
        }

        when (config.routingMode) {
            RoutingMode.SELECTED_APPS -> {
                config.selectedApps
                    .filterNot { it == packageName }
                    .forEach { selectedPackage ->
                        try {
                            builder.addAllowedApplication(selectedPackage)
                        } catch (_: PackageManager.NameNotFoundException) {
                            sessionRepository.appendLog(LogLevel.WARN, "Skipping missing package: $selectedPackage")
                        }
                    }
            }

            RoutingMode.ALL_APPS -> {
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (_: PackageManager.NameNotFoundException) {
                    sessionRepository.appendLog(LogLevel.WARN, "Failed to disallow TrueRoute package from VPN")
                }
            }
        }

        return builder.establish()
    }

    private fun buildSessionName(config: ProxyConfig): String {
        val routingLabel = if (config.routingMode == RoutingMode.ALL_APPS) "All apps" else "Selected apps"
        val dnsLabel = if (config.dnsMode == DnsMode.PROVIDER) "Provider DNS" else "Custom DNS"
        return "TrueRoute - $routingLabel - $dnsLabel"
    }

    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = serviceScope.launch {
            while (isActive && tunnelInterface != null) {
                if (!nativeTunnel.TProxyIsRunning()) {
                    reportError("Native tunnel stopped unexpectedly: ${nativeResultMessage(nativeTunnel.TProxyGetLastResult())}")
                    stopTunnel(stopServiceSelf = true, preserveError = true)
                    break
                }

                val stats = nativeTunnel.TProxyGetStats()
                if (stats.size >= 4) {
                    sessionRepository.updateStats(
                        TunnelStats(
                            txPackets = stats[0],
                            txBytes = stats[1],
                            rxPackets = stats[2],
                            rxBytes = stats[3],
                        ),
                    )
                }

                nativeRuntimeFiles?.logFile?.let(::drainNativeLogFile)
                delay(STATS_POLL_INTERVAL_MS)
            }
        }
    }

    private fun startNativeLogPolling(logFile: File) {
        nativeLogOffset = 0L
        nativeLogJob?.cancel()
        nativeLogJob = serviceScope.launch {
            while (isActive) {
                drainNativeLogFile(logFile)
                delay(NATIVE_LOG_POLL_INTERVAL_MS)
            }
        }
    }

    private fun drainNativeLogFile(logFile: File) {
        if (!logFile.exists()) {
            return
        }

        runCatching {
            RandomAccessFile(logFile, "r").use { reader ->
                if (nativeLogOffset > reader.length()) {
                    nativeLogOffset = 0L
                }
                reader.seek(nativeLogOffset)

                var line = reader.readLine()
                while (line != null) {
                    appendNativeLogLine(line)
                    line = reader.readLine()
                }

                nativeLogOffset = reader.filePointer
            }
        }
    }

    private fun appendNativeLogLine(rawLine: String) {
        val line = rawLine.trim()
        if (line.isEmpty()) {
            return
        }

        val match = NATIVE_LOG_PATTERN.matchEntire(line)
        val level = when (match?.groupValues?.getOrNull(1)) {
            "D" -> LogLevel.DEBUG
            "I" -> LogLevel.INFO
            "W" -> LogLevel.WARN
            "E" -> LogLevel.ERROR
            else -> LogLevel.DEBUG
        }
        val message = match?.groupValues?.getOrNull(2).orEmpty().ifBlank { line }
        sessionRepository.appendLog(level, "native: $message")
    }

    private suspend fun stopNativeLogPolling() {
        nativeRuntimeFiles?.logFile?.let(::drainNativeLogFile)
        nativeLogJob?.cancelAndJoin()
        nativeLogJob = null
        nativeRuntimeFiles = null
        nativeLogOffset = 0L
    }

    private suspend fun stopTunnel(stopServiceSelf: Boolean, preserveError: Boolean = false) {
        val hadTunnel = tunnelInterface != null || nativeTunnel.TProxyIsRunning()
        if (!hadTunnel) {
            stopNativeLogPolling()
            if (stopServiceSelf) {
                stopSelf()
            }
            return
        }

        if (!preserveError) {
            sessionRepository.updatePhase(TunnelPhase.DISCONNECTING, "Stopping tunnel")
            sessionRepository.appendLog(LogLevel.INFO, "Stopping tunnel")
        } else {
            sessionRepository.appendLog(LogLevel.WARN, "Stopping tunnel after error")
        }

        statsJob?.cancelAndJoin()
        statsJob = null

        runCatching { nativeTunnel.TProxyStopService() }
            .onFailure {
                sessionRepository.appendLog(LogLevel.WARN, "Native stop failed: ${it.message.orEmpty()}")
            }

        runCatching { tunnelInterface?.close() }
            .onFailure {
                sessionRepository.appendLog(LogLevel.WARN, "Failed to close TUN fd: ${it.message.orEmpty()}")
            }
        tunnelInterface = null

        stopNativeLogPolling()
        stopForeground(STOP_FOREGROUND_REMOVE)
        sessionRepository.updateStats(TunnelStats())

        if (!preserveError) {
            sessionRepository.updatePhase(TunnelPhase.IDLE, "Disconnected")
            sessionRepository.appendLog(LogLevel.INFO, "Disconnected")
        }

        if (stopServiceSelf) {
            stopSelf()
        }
    }

    private fun reportError(message: String) {
        sessionRepository.appendLog(LogLevel.ERROR, message)
        sessionRepository.updatePhase(TunnelPhase.ERROR, message)
    }

    private fun nativeResultMessage(code: Int): String = when (code) {
        0 -> "normal shutdown"
        -1 -> "configuration parse failed"
        -2 -> "logger init failed"
        -3 -> "SOCKS5 logger init failed"
        -4 -> "task system init failed"
        -5 -> "tunnel init failed"
        -1000 -> "start requested but result not available yet"
        -1001 -> "native worker thread creation failed"
        else -> "error code $code"
    }

    private fun startForegroundWithNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }

        val openIntent = Intent(this, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_trueroute)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_CONNECT: String = "com.trueroute.app.CONNECT"
        const val ACTION_DISCONNECT: String = "com.trueroute.app.DISCONNECT"

        private const val NOTIFICATION_CHANNEL_ID = "trueroute_tunnel"
        private const val NOTIFICATION_ID = 1001
        private const val STATS_POLL_INTERVAL_MS = 1_000L
        private const val NATIVE_LOG_POLL_INTERVAL_MS = 500L
        private const val NATIVE_START_GRACE_PERIOD_MS = 750L
        private const val DEFAULT_CUSTOM_DNS = "8.8.8.8"
        private val NATIVE_LOG_PATTERN = Regex("""^\[[^\]]+\]\s+\[([DIWE])\]\s+(.*)$""")
    }
}