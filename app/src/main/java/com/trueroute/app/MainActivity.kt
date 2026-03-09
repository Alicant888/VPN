package com.trueroute.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.trueroute.app.model.TunnelPhase
import com.trueroute.app.ui.MainScreen
import com.trueroute.app.ui.MainViewModel
import com.trueroute.app.validation.ProxyConfigValidation
import com.trueroute.app.validation.ProxyConfigValidator
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val appContainer by lazy { (application as TrueRouteApp).container }
    private val viewModel by viewModels<MainViewModel> { MainViewModel.factory(appContainer) }

    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            appContainer.tunnelController.start()
        } else {
            viewModel.showValidation("VPN permission was denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainScreen(
                        uiState = uiState,
                        onProxyHostChanged = viewModel::onProxyHostChanged,
                        onProxyPortChanged = viewModel::onProxyPortChanged,
                        onUsernameChanged = viewModel::onUsernameChanged,
                        onPasswordChanged = viewModel::onPasswordChanged,
                        onDnsModeChanged = viewModel::onDnsModeChanged,
                        onCustomDnsChanged = viewModel::onCustomDnsChanged,
                        onRoutingModeChanged = viewModel::onRoutingModeChanged,
                        onAutoStartOnLaunchChanged = viewModel::onAutoStartOnLaunchChanged,
                        onAppPickerVisibilityChanged = viewModel::onAppPickerVisibilityChanged,
                        onAppSelectionChanged = viewModel::onAppSelectionToggled,
                        onConnectToggle = ::toggleConnection,
                    )
                }
            }
        }

        if (savedInstanceState == null) {
            maybeAutoStartOnLaunch()
        }
    }

    private fun toggleConnection() {
        when (viewModel.uiState.value.status.phase) {
            TunnelPhase.CONNECTED,
            TunnelPhase.CONNECTING,
            TunnelPhase.DISCONNECTING -> appContainer.tunnelController.stop()

            TunnelPhase.IDLE,
            TunnelPhase.ERROR -> requestConnection(viewModel.validateForConnect())
        }
    }

    private fun maybeAutoStartOnLaunch() {
        lifecycleScope.launch {
            val form = appContainer.proxyConfigRepository.readForm()
            if (!form.autoStartOnLaunch) {
                return@launch
            }

            val validationError = when (val validation = ProxyConfigValidator.validate(form)) {
                is ProxyConfigValidation.Invalid -> validation.reason
                is ProxyConfigValidation.Valid -> null
            }

            requestConnection(validationError)
        }
    }

    private fun requestConnection(validationError: String?) {
        if (validationError != null) {
            viewModel.showValidation(validationError)
            return
        }

        val permissionIntent = appContainer.tunnelController.preparePermissionIntent(this)
        if (permissionIntent == null) {
            appContainer.tunnelController.start()
        } else {
            vpnPermissionLauncher.launch(permissionIntent)
        }
    }
}