package com.trueroute.app.model

data class ProxyConfig(
    val proxyHost: String,
    val proxyPort: Int,
    val username: String,
    val password: String,
    val dnsMode: DnsMode,
    val customDns: String?,
    val routingMode: RoutingMode,
    val selectedApps: Set<String>,
)

data class ProxyConfigForm(
    val proxyHost: String = "",
    val proxyPort: String = "1080",
    val username: String = "",
    val password: String = "",
    val dnsMode: DnsMode = DnsMode.PROVIDER,
    val customDns: String = "8.8.8.8",
    val routingMode: RoutingMode = RoutingMode.ALL_APPS,
    val selectedApps: Set<String> = emptySet(),
    val autoStartOnLaunch: Boolean = false,
)