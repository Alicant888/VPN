package com.trueroute.app.validation

import com.trueroute.app.model.DnsMode
import com.trueroute.app.model.ProxyConfigForm
import com.trueroute.app.model.RoutingMode
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyConfigValidatorTest {
    @Test
    fun customDnsRequiresIpLiteral() {
        val result = ProxyConfigValidator.validate(
            ProxyConfigForm(
                proxyHost = "127.0.0.1",
                proxyPort = "1080",
                dnsMode = DnsMode.CUSTOM,
                customDns = "dns.google",
            ),
        )

        assertTrue(result is ProxyConfigValidation.Invalid)
    }

    @Test
    fun selectedAppsRoutingRequiresAtLeastOnePackage() {
        val result = ProxyConfigValidator.validate(
            ProxyConfigForm(
                proxyHost = "127.0.0.1",
                proxyPort = "1080",
                routingMode = RoutingMode.SELECTED_APPS,
                selectedApps = emptySet(),
            ),
        )

        assertTrue(result is ProxyConfigValidation.Invalid)
    }

    @Test
    fun validConfigurationPassesValidation() {
        val result = ProxyConfigValidator.validate(
            ProxyConfigForm(
                proxyHost = "127.0.0.1",
                proxyPort = "1080",
                dnsMode = DnsMode.CUSTOM,
                customDns = "8.8.8.8",
                routingMode = RoutingMode.SELECTED_APPS,
                selectedApps = setOf("com.base.app"),
            ),
        )

        assertTrue(result is ProxyConfigValidation.Valid)
    }
}
