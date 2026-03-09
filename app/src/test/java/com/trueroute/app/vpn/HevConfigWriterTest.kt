package com.trueroute.app.vpn

import com.trueroute.app.model.DnsMode
import com.trueroute.app.model.ProxyConfig
import com.trueroute.app.model.RoutingMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HevConfigWriterTest {
    @Test
    fun providerDnsConfig_enablesMapDns() {
        val config = ProxyConfig(
            proxyHost = "10.10.10.10",
            proxyPort = 1080,
            username = "user",
            password = "pass",
            dnsMode = DnsMode.PROVIDER,
            customDns = null,
            routingMode = RoutingMode.ALL_APPS,
            selectedApps = emptySet(),
        )

        val yaml = HevConfigWriter.buildConfig(config)

        assertTrue(yaml.contains("mapdns:"))
        assertTrue(yaml.contains("username: 'user'"))
        assertTrue(yaml.contains("udp: 'udp'"))
    }

    @Test
    fun customDnsConfig_skipsMapDns() {
        val config = ProxyConfig(
            proxyHost = "10.10.10.10",
            proxyPort = 1080,
            username = "",
            password = "",
            dnsMode = DnsMode.CUSTOM,
            customDns = "8.8.8.8",
            routingMode = RoutingMode.SELECTED_APPS,
            selectedApps = setOf("com.base.app"),
        )

        val yaml = HevConfigWriter.buildConfig(config)

        assertFalse(yaml.contains("mapdns:"))
        assertFalse(yaml.contains("username:"))
    }
}
