package com.trueroute.app.validation

import com.trueroute.app.model.DnsMode
import com.trueroute.app.model.ProxyConfig
import com.trueroute.app.model.ProxyConfigForm
import com.trueroute.app.model.RoutingMode
import java.net.InetAddress
import kotlin.text.Charsets

sealed interface ProxyConfigValidation {
    data class Valid(val config: ProxyConfig) : ProxyConfigValidation
    data class Invalid(val reason: String) : ProxyConfigValidation
}

object ProxyConfigValidator {
    fun validate(form: ProxyConfigForm): ProxyConfigValidation {
        val host = form.proxyHost.trim()
        if (host.isEmpty()) {
            return ProxyConfigValidation.Invalid("Proxy host is required")
        }

        val port = form.proxyPort.trim().toIntOrNull()
            ?: return ProxyConfigValidation.Invalid("Proxy port must be a valid number")
        if (port !in 1..65535) {
            return ProxyConfigValidation.Invalid("Proxy port must be between 1 and 65535")
        }

        if (form.username.toByteArray(Charsets.UTF_8).size > 255) {
            return ProxyConfigValidation.Invalid("SOCKS5 username must be at most 255 bytes")
        }

        if (form.password.toByteArray(Charsets.UTF_8).size > 255) {
            return ProxyConfigValidation.Invalid("SOCKS5 password must be at most 255 bytes")
        }

        val customDns = form.customDns.trim()
        if (form.dnsMode == DnsMode.CUSTOM && !isIpLiteral(customDns)) {
            return ProxyConfigValidation.Invalid("Custom DNS must be a valid IPv4 or IPv6 address")
        }

        if (form.routingMode == RoutingMode.SELECTED_APPS && form.selectedApps.isEmpty()) {
            return ProxyConfigValidation.Invalid("Select at least one app for per-app routing")
        }

        return ProxyConfigValidation.Valid(
            ProxyConfig(
                proxyHost = host,
                proxyPort = port,
                username = form.username.trim(),
                password = form.password,
                dnsMode = form.dnsMode,
                customDns = customDns.takeIf { it.isNotEmpty() },
                routingMode = form.routingMode,
                selectedApps = form.selectedApps,
            ),
        )
    }

    private fun isIpLiteral(value: String): Boolean {
        if (value.isBlank()) {
            return false
        }

        return when {
            value.contains(':') -> {
                value.matches(Regex("^[0-9A-Fa-f:.]+$")) && runCatching { InetAddress.getByName(value) }.isSuccess
            }

            value.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")) -> {
                value.split('.').all { segment -> segment.toIntOrNull() in 0..255 }
            }

            else -> false
        }
    }
}