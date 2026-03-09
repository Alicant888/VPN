package com.trueroute.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trueroute.app.model.DnsMode
import com.trueroute.app.model.ProxyConfigForm
import com.trueroute.app.model.RoutingMode
import com.trueroute.app.model.UdpRelayMode
import kotlinx.coroutines.flow.first

private val Context.proxyDataStore by preferencesDataStore(name = "trueroute_config")

class ProxyConfigRepository(
    private val context: Context,
    private val proxySecretsStore: ProxySecretsStore,
) {
    suspend fun readForm(): ProxyConfigForm {
        val preferences = context.proxyDataStore.data.first()
        return ProxyConfigForm(
            proxyHost = preferences[KEY_PROXY_HOST].orEmpty(),
            proxyPort = preferences[KEY_PROXY_PORT]?.toString() ?: DEFAULT_PROXY_PORT,
            username = proxySecretsStore.readUsername(),
            password = proxySecretsStore.readPassword(),
            dnsMode = preferences[KEY_DNS_MODE]?.let(DnsMode::valueOf) ?: DnsMode.PROVIDER,
            customDns = preferences[KEY_CUSTOM_DNS] ?: DEFAULT_DNS,
            routingMode = preferences[KEY_ROUTING_MODE]?.let(RoutingMode::valueOf) ?: RoutingMode.ALL_APPS,
            selectedApps = preferences[KEY_SELECTED_APPS] ?: emptySet(),
            autoStartOnLaunch = preferences[KEY_AUTO_START_ON_LAUNCH] ?: false,
            udpRelayMode = preferences[KEY_UDP_RELAY_MODE]?.let(UdpRelayMode::valueOf) ?: UdpRelayMode.UDP_ASSOCIATE,
        )
    }

    suspend fun saveForm(form: ProxyConfigForm) {
        context.proxyDataStore.edit { preferences ->
            preferences[KEY_PROXY_HOST] = form.proxyHost.trim()
            preferences[KEY_PROXY_PORT] = form.proxyPort.trim().toIntOrNull() ?: DEFAULT_PROXY_PORT.toInt()
            preferences[KEY_DNS_MODE] = form.dnsMode.name
            preferences[KEY_CUSTOM_DNS] = form.customDns.trim().ifEmpty { DEFAULT_DNS }
            preferences[KEY_ROUTING_MODE] = form.routingMode.name
            preferences[KEY_SELECTED_APPS] = form.selectedApps
            preferences[KEY_AUTO_START_ON_LAUNCH] = form.autoStartOnLaunch
            preferences[KEY_UDP_RELAY_MODE] = form.udpRelayMode.name
        }
        proxySecretsStore.write(form.username.trim(), form.password)
    }

    private companion object {
        private const val DEFAULT_PROXY_PORT = "1080"
        private const val DEFAULT_DNS = "8.8.8.8"

        private val KEY_PROXY_HOST = stringPreferencesKey("proxy_host")
        private val KEY_PROXY_PORT = intPreferencesKey("proxy_port")
        private val KEY_DNS_MODE = stringPreferencesKey("dns_mode")
        private val KEY_CUSTOM_DNS = stringPreferencesKey("custom_dns")
        private val KEY_ROUTING_MODE = stringPreferencesKey("routing_mode")
        private val KEY_SELECTED_APPS = stringSetPreferencesKey("selected_apps")
        private val KEY_AUTO_START_ON_LAUNCH = booleanPreferencesKey("auto_start_on_launch")
        private val KEY_UDP_RELAY_MODE = stringPreferencesKey("udp_relay_mode")
    }
}
