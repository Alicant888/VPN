package com.trueroute.app.ui

import com.trueroute.app.model.InstalledApp
import com.trueroute.app.model.LogEntry
import com.trueroute.app.model.ProxyConfigForm
import com.trueroute.app.model.TunnelStatus

data class MainUiState(
    val form: ProxyConfigForm = ProxyConfigForm(),
    val status: TunnelStatus = TunnelStatus(),
    val logs: List<LogEntry> = emptyList(),
    val installedApps: List<InstalledApp> = emptyList(),
    val isAppPickerVisible: Boolean = false,
    val validationMessage: String? = null,
)
