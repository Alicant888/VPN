package com.trueroute.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.trueroute.app.model.DnsMode
import com.trueroute.app.model.LogEntry
import com.trueroute.app.model.RoutingMode
import com.trueroute.app.model.TunnelPhase
import com.trueroute.app.model.UdpRelayMode
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ln
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onProxyHostChanged: (String) -> Unit,
    onProxyPortChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onUdpRelayModeChanged: (UdpRelayMode) -> Unit,
    onDnsModeChanged: (DnsMode) -> Unit,
    onCustomDnsChanged: (String) -> Unit,
    onRoutingModeChanged: (RoutingMode) -> Unit,
    onAutoStartOnLaunchChanged: (Boolean) -> Unit,
    onAppPickerVisibilityChanged: (Boolean) -> Unit,
    onAppSelectionChanged: (String, Boolean) -> Unit,
    onConnectToggle: () -> Unit,
) {
    val isBusy = uiState.status.phase == TunnelPhase.CONNECTING || uiState.status.phase == TunnelPhase.DISCONNECTING
    val isConnected = uiState.status.phase == TunnelPhase.CONNECTED
    val settingsEditable = uiState.status.phase == TunnelPhase.IDLE || uiState.status.phase == TunnelPhase.ERROR

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TrueRoute") },
                actions = {
                    Button(onClick = onConnectToggle, enabled = !isBusy) {
                        Icon(
                            imageVector = if (isConnected) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isConnected) "Disconnect" else "Connect")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }
            item { StatusCard(uiState) }
            if (!settingsEditable) {
                item {
                    Text(
                        "Disconnect the tunnel to edit settings.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                ProxyCard(
                    uiState = uiState,
                    enabled = settingsEditable,
                    onProxyHostChanged = onProxyHostChanged,
                    onProxyPortChanged = onProxyPortChanged,
                    onUsernameChanged = onUsernameChanged,
                    onPasswordChanged = onPasswordChanged,
                )
            }
            item {
                RelayModeCard(
                    uiState = uiState,
                    enabled = settingsEditable,
                    onUdpRelayModeChanged = onUdpRelayModeChanged,
                )
            }
            item {
                DnsCard(
                    uiState = uiState,
                    enabled = settingsEditable,
                    onDnsModeChanged = onDnsModeChanged,
                    onCustomDnsChanged = onCustomDnsChanged,
                )
            }
            item {
                RoutingCard(
                    uiState = uiState,
                    enabled = settingsEditable,
                    onRoutingModeChanged = onRoutingModeChanged,
                    onAppPickerVisibilityChanged = onAppPickerVisibilityChanged,
                )
            }
            item {
                StartupCard(
                    uiState = uiState,
                    enabled = settingsEditable,
                    onAutoStartOnLaunchChanged = onAutoStartOnLaunchChanged,
                )
            }
            item { LogsCard(uiState.logs) }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (uiState.isAppPickerVisible) {
        AppPickerDialog(
            uiState = uiState,
            enabled = settingsEditable,
            onDismiss = { onAppPickerVisibilityChanged(false) },
            onAppSelectionChanged = onAppSelectionChanged,
        )
    }
}

@Composable
private fun StatusCard(uiState: MainUiState) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = phaseColor(uiState.status.phase))
                Column {
                    Text(uiState.status.phase.name.replace('_', ' '), fontWeight = FontWeight.Bold)
                    uiState.status.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            uiState.validationMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider()
            Text(
                "TX ${formatBytes(uiState.status.stats.txBytes)} / ${uiState.status.stats.txPackets} pkts - RX ${formatBytes(uiState.status.stats.rxBytes)} / ${uiState.status.stats.rxPackets} pkts",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ProxyCard(
    uiState: MainUiState,
    enabled: Boolean,
    onProxyHostChanged: (String) -> Unit,
    onProxyPortChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("SOCKS5 Proxy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = uiState.form.proxyHost,
                onValueChange = onProxyHostChanged,
                enabled = enabled,
                label = { Text("Proxy IP / Host") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = uiState.form.proxyPort,
                onValueChange = onProxyPortChanged,
                enabled = enabled,
                label = { Text("Port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = uiState.form.username,
                onValueChange = onUsernameChanged,
                enabled = enabled,
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = uiState.form.password,
                onValueChange = onPasswordChanged,
                enabled = enabled,
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
        }
    }
}

@Composable
private fun RelayModeCard(
    uiState: MainUiState,
    enabled: Boolean,
    onUdpRelayModeChanged: (UdpRelayMode) -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Relay Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.form.udpRelayMode == UdpRelayMode.UDP_ASSOCIATE,
                    enabled = enabled,
                    onClick = { onUdpRelayModeChanged(UdpRelayMode.UDP_ASSOCIATE) },
                    label = { Text("Standard UDP") },
                )
                FilterChip(
                    selected = uiState.form.udpRelayMode == UdpRelayMode.TCP_FALLBACK,
                    enabled = enabled,
                    onClick = { onUdpRelayModeChanged(UdpRelayMode.TCP_FALLBACK) },
                    label = { Text("TCP fallback") },
                )
            }
            if (uiState.form.udpRelayMode == UdpRelayMode.TCP_FALLBACK) {
                Text(
                    "Routes UDP through hev's proprietary UDP-in-TCP extension. Use this only if your proxy provider supports it or to test apps that misbehave with standard UDP relay.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    "Uses standard SOCKS5 UDP Associate for DNS, QUIC and other UDP traffic.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun DnsCard(
    uiState: MainUiState,
    enabled: Boolean,
    onDnsModeChanged: (DnsMode) -> Unit,
    onCustomDnsChanged: (String) -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Smart DNS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.form.dnsMode == DnsMode.PROVIDER,
                    enabled = enabled,
                    onClick = { onDnsModeChanged(DnsMode.PROVIDER) },
                    label = { Text("Provider DNS") },
                )
                FilterChip(
                    selected = uiState.form.dnsMode == DnsMode.CUSTOM,
                    enabled = enabled,
                    onClick = { onDnsModeChanged(DnsMode.CUSTOM) },
                    label = { Text("Custom DNS") },
                )
            }
            if (uiState.form.dnsMode == DnsMode.CUSTOM) {
                OutlinedTextField(
                    value = uiState.form.customDns,
                    onValueChange = onCustomDnsChanged,
                    enabled = enabled,
                    label = { Text("Custom DNS server") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            } else {
                Text(
                    "DNS requests are redirected to an internal mapped endpoint so resolution stays on the proxy side.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun RoutingCard(
    uiState: MainUiState,
    enabled: Boolean,
    onRoutingModeChanged: (RoutingMode) -> Unit,
    onAppPickerVisibilityChanged: (Boolean) -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Routing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.form.routingMode == RoutingMode.ALL_APPS,
                    enabled = enabled,
                    onClick = { onRoutingModeChanged(RoutingMode.ALL_APPS) },
                    label = { Text("All apps") },
                )
                FilterChip(
                    selected = uiState.form.routingMode == RoutingMode.SELECTED_APPS,
                    enabled = enabled,
                    onClick = { onRoutingModeChanged(RoutingMode.SELECTED_APPS) },
                    label = { Text("Selected apps") },
                )
            }
            if (uiState.form.routingMode == RoutingMode.SELECTED_APPS) {
                Text("${uiState.form.selectedApps.size} apps selected", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { onAppPickerVisibilityChanged(true) }, enabled = enabled) {
                    Text("Choose apps")
                }
                if (uiState.form.selectedApps.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        uiState.installedApps
                            .filter { it.packageName in uiState.form.selectedApps }
                            .take(6)
                            .forEach { app ->
                                Text("- ${app.label} (${app.packageName})", style = MaterialTheme.typography.bodySmall)
                            }
                    }
                }
            } else {
                Text("All device traffic except TrueRoute itself will be captured by the VPN.")
            }
        }
    }
}

@Composable
private fun StartupCard(
    uiState: MainUiState,
    enabled: Boolean,
    onAutoStartOnLaunchChanged: (Boolean) -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Startup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = uiState.form.autoStartOnLaunch,
                    onCheckedChange = onAutoStartOnLaunchChanged,
                    enabled = enabled,
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text("Auto-start after launch", fontWeight = FontWeight.Medium)
                    Text(
                        "TrueRoute will automatically try to connect when the app opens using the saved proxy settings.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun LogsCard(logs: List<LogEntry>) {
    val clipboardManager = LocalClipboardManager.current

    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Session log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(
                    onClick = {
                        clipboardManager.setText(
                            AnnotatedString(logs.joinToString(separator = "\n") { entry -> formatLog(entry) }),
                        )
                    },
                    enabled = logs.isNotEmpty(),
                ) {
                    Text("Copy")
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .padding(12.dp),
            ) {
                if (logs.isEmpty()) {
                    Text("No session events yet.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(logs) { entry ->
                            Text(formatLog(entry), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPickerDialog(
    uiState: MainUiState,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onAppSelectionChanged: (String, Boolean) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Select apps to route", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Box(modifier = Modifier.height(420.dp)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.installedApps) { app ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = app.packageName in uiState.form.selectedApps,
                                    onCheckedChange = { checked -> onAppSelectionChanged(app.packageName, checked) },
                                    enabled = enabled,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.label)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

private fun phaseColor(phase: TunnelPhase): Color = when (phase) {
    TunnelPhase.CONNECTED -> Color(0xFF16A34A)
    TunnelPhase.CONNECTING, TunnelPhase.DISCONNECTING -> Color(0xFFEA580C)
    TunnelPhase.ERROR -> Color(0xFFDC2626)
    TunnelPhase.IDLE -> Color(0xFF64748B)
}

private fun formatLog(entry: LogEntry): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
    return "${formatter.format(entry.timestamp)} [${entry.level.name}] ${entry.message}"
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) {
        return "0 B"
    }
    val units = listOf("B", "KB", "MB", "GB")
    val digitGroup = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceAtMost(units.lastIndex)
    val value = bytes / 1024.0.pow(digitGroup.toDouble())
    return "%.1f %s".format(value, units[digitGroup])
}
