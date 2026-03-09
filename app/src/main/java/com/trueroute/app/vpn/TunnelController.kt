package com.trueroute.app.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat

class TunnelController(
    private val appContext: Context,
) {
    fun preparePermissionIntent(context: Context): Intent? = VpnService.prepare(context)

    fun start() {
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, TrueRouteVpnService::class.java).setAction(TrueRouteVpnService.ACTION_CONNECT),
        )
    }

    fun stop() {
        appContext.startService(
            Intent(appContext, TrueRouteVpnService::class.java).setAction(TrueRouteVpnService.ACTION_DISCONNECT),
        )
    }
}
