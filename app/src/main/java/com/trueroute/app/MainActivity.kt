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
import com.trueroute.app.model.TunnelPhase
import com.trueroute.app.ui.MainScreen
import com.trueroute.app.ui.MainViewModel

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
                        onAppPickerVisibilityChanged = viewModel::onAppPickerVisibilityChanged,
                        onAppSelectionChanged = viewModel::onAppSelectionToggled,
                        onConnectToggle = ::toggleConnection,
                    )
                }
            }
        }
    }

    private fun toggleConnection() {
        when (viewModel.uiState.value.status.phase) {
            TunnelPhase.CONNECTED,
            TunnelPhase.CONNECTING,
            TunnelPhase.DISCONNECTING -> appContainer.tunnelController.stop()

            TunnelPhase.IDLE,
            TunnelPhase.ERROR -> {
                val validationError = viewModel.validateForConnect()
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
    }
}
