package com.trueroute.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trueroute.app.AppContainer
import com.trueroute.app.model.DnsMode
import com.trueroute.app.model.ProxyConfigForm
import com.trueroute.app.model.RoutingMode
import com.trueroute.app.validation.ProxyConfigValidation
import com.trueroute.app.validation.ProxyConfigValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val appContainer: AppContainer,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(form = appContainer.proxyConfigRepository.readForm()) }
        }
        viewModelScope.launch {
            _uiState.update { it.copy(installedApps = appContainer.installedAppsRepository.loadInstalledApps()) }
        }
        viewModelScope.launch {
            appContainer.tunnelSessionRepository.status.collectLatest { status ->
                _uiState.update { it.copy(status = status) }
            }
        }
        viewModelScope.launch {
            appContainer.tunnelSessionRepository.logs.collectLatest { logs ->
                _uiState.update { it.copy(logs = logs) }
            }
        }
    }

    fun onProxyHostChanged(value: String) = updateForm { it.copy(proxyHost = value) }

    fun onProxyPortChanged(value: String) = updateForm { it.copy(proxyPort = value.filter(Char::isDigit).take(5)) }

    fun onUsernameChanged(value: String) = updateForm { it.copy(username = value) }

    fun onPasswordChanged(value: String) = updateForm { it.copy(password = value) }

    fun onDnsModeChanged(value: DnsMode) = updateForm { it.copy(dnsMode = value) }

    fun onCustomDnsChanged(value: String) = updateForm { it.copy(customDns = value) }

    fun onRoutingModeChanged(value: RoutingMode) = updateForm { it.copy(routingMode = value) }

    fun onAutoStartOnLaunchChanged(value: Boolean) = updateForm { it.copy(autoStartOnLaunch = value) }

    fun onAppPickerVisibilityChanged(visible: Boolean) {
        _uiState.update { it.copy(isAppPickerVisible = visible) }
    }

    fun onAppSelectionToggled(packageName: String, selected: Boolean) {
        updateForm { current ->
            val nextSelection = current.selectedApps.toMutableSet().apply {
                if (selected) add(packageName) else remove(packageName)
            }
            current.copy(selectedApps = nextSelection)
        }
    }

    fun validateForConnect(): String? = when (val result = ProxyConfigValidator.validate(_uiState.value.form)) {
        is ProxyConfigValidation.Invalid -> result.reason
        is ProxyConfigValidation.Valid -> null
    }

    fun showValidation(message: String?) {
        _uiState.update { it.copy(validationMessage = message) }
    }

    private fun updateForm(transform: (ProxyConfigForm) -> ProxyConfigForm) {
        val updated = transform(_uiState.value.form)
        _uiState.update { it.copy(form = updated, validationMessage = null) }
        viewModelScope.launch {
            appContainer.proxyConfigRepository.saveForm(updated)
        }
    }

    companion object {
        fun factory(appContainer: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(appContainer) as T
            }
        }
    }
}