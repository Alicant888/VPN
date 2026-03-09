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
        sessionRepository.updatePhase(TunnelPhase.CONNECTING, "Preparing VPN tunnel")
        sessionRepository.appendLog(LogLevel.INFO, "Preparing VPN tunnel")

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

        val vpnInterface = establishTunnel(config)
        if (vpnInterface == null) {
            reportError("Failed to establish Android VPN interface")
            stopSelf()
            return
        }
        tunnelInterface = vpnInterface

        try {
            startForegroundWithNotification()
            val configFile = HevConfigWriter.writeConfig(cacheDir, config)
            nativeTunnel.TProxyStartService(configFile.absolutePath, vpnInterface.fd)
            sessionRepository.appendLog(LogLevel.INFO, "SOCKS5 tunnel started with UDP Associate")
            sessionRepository.updatePhase(
                TunnelPhase.CONNECTED,
                "Connected via ${config.proxyHost}:${config.proxyPort}",
            )
            startStatsPolling()
        } catch (error: UnsatisfiedLinkError) {
            reportError("Native tunnel library is unavailable: ${error.message.orEmpty()}")
            stopTunnel(stopServiceSelf = true)
        } catch (error: Exception) {
            reportError("Tunnel start failed: ${error.message.orEmpty()}")
            stopTunnel(stopServiceSelf = true)
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

        if (config.routingMode == RoutingMode.SELECTED_APPS) {
            config.selectedApps.forEach { packageName ->
                try {
                    builder.addAllowedApplication(packageName)
                } catch (_: PackageManager.NameNotFoundException) {
                    sessionRepository.appendLog(LogLevel.WARN, "Skipping missing package: $packageName")
                }
            }
        }

        try {
            builder.addDisallowedApplication(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            sessionRepository.appendLog(LogLevel.WARN, "Failed to disallow TrueRoute package from VPN")
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
                delay(STATS_POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun stopTunnel(stopServiceSelf: Boolean) {
        if (tunnelInterface == null) {
            if (stopServiceSelf) {
                stopSelf()
            }
            return
        }

        sessionRepository.updatePhase(TunnelPhase.DISCONNECTING, "Stopping tunnel")
        sessionRepository.appendLog(LogLevel.INFO, "Stopping tunnel")

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

        stopForeground(STOP_FOREGROUND_REMOVE)
        sessionRepository.updateStats(TunnelStats())
        sessionRepository.updatePhase(TunnelPhase.IDLE, "Disconnected")
        sessionRepository.appendLog(LogLevel.INFO, "Disconnected")

        if (stopServiceSelf) {
            stopSelf()
        }
    }

    private fun reportError(message: String) {
        sessionRepository.appendLog(LogLevel.ERROR, message)
        sessionRepository.updatePhase(TunnelPhase.ERROR, message)
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
        private const val DEFAULT_CUSTOM_DNS = "8.8.8.8"
    }
}




