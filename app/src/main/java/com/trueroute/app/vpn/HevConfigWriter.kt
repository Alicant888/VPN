package com.trueroute.app.vpn

import com.trueroute.app.model.DnsMode
import com.trueroute.app.model.ProxyConfig
import com.trueroute.app.model.UdpRelayMode
import java.io.File

data class HevRuntimeFiles(
    val configFile: File,
    val logFile: File,
)

object HevConfigWriter {
    const val TUNNEL_MTU: Int = 8500
    const val TUNNEL_IPV4: String = "198.18.0.1"
    const val TUNNEL_IPV6: String = "fc00::1"
    const val MAPPED_DNS: String = "198.18.0.2"
    private const val MAPPED_DNS_NETWORK: String = "100.64.0.0"
    private const val MAPPED_DNS_NETMASK: String = "255.192.0.0"

    fun writeConfig(cacheDir: File, config: ProxyConfig): HevRuntimeFiles {
        val configFile = File(cacheDir, "trueroute-tunnel.yml")
        val logFile = File(cacheDir, "trueroute-native.log")
        logFile.writeText("")
        configFile.writeText(buildConfig(config, logFile.absolutePath))
        return HevRuntimeFiles(configFile = configFile, logFile = logFile)
    }

    fun buildConfig(config: ProxyConfig, logFilePath: String? = null): String = buildString {
        appendLine("misc:")
        appendLine("  task-stack-size: 81920")
        appendLine("  connect-timeout: 10000")
        appendLine("  tcp-read-write-timeout: 300000")
        appendLine("  udp-read-write-timeout: 60000")
        appendLine("  log-level: debug")
        logFilePath?.takeIf { it.isNotBlank() }?.let {
            appendLine("  log-file: ${yamlString(it)}")
        }
        appendLine("tunnel:")
        appendLine("  mtu: $TUNNEL_MTU")
        appendLine("  ipv4: $TUNNEL_IPV4")
        appendLine("  ipv6: ${yamlString(TUNNEL_IPV6)}")
        appendLine("socks5:")
        appendLine("  port: ${config.proxyPort}")
        appendLine("  address: ${yamlString(config.proxyHost)}")
        appendLine("  udp: ${yamlString(config.udpRelayMode.toHevValue())}")
        if (config.username.isNotEmpty() && config.password.isNotEmpty()) {
            appendLine("  username: ${yamlString(config.username)}")
            appendLine("  password: ${yamlString(config.password)}")
        }
        if (config.dnsMode == DnsMode.PROVIDER) {
            appendLine("mapdns:")
            appendLine("  address: $MAPPED_DNS")
            appendLine("  port: 53")
            appendLine("  network: $MAPPED_DNS_NETWORK")
            appendLine("  netmask: $MAPPED_DNS_NETMASK")
            appendLine("  cache-size: 10000")
        }
    }

    private fun UdpRelayMode.toHevValue(): String = when (this) {
        UdpRelayMode.UDP_ASSOCIATE -> "udp"
        UdpRelayMode.TCP_FALLBACK -> "tcp"
    }

    private fun yamlString(value: String): String = "'${value.replace("'", "''")}'"
}
