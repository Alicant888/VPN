package com.trueroute.app

import android.content.Context
import com.trueroute.app.data.InstalledAppsRepository
import com.trueroute.app.data.ProxyConfigRepository
import com.trueroute.app.data.ProxySecretsStore
import com.trueroute.app.data.TunnelSessionRepository
import com.trueroute.app.vpn.TunnelController

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val proxySecretsStore = ProxySecretsStore(appContext)
    val proxyConfigRepository = ProxyConfigRepository(appContext, proxySecretsStore)
    val installedAppsRepository = InstalledAppsRepository(appContext)
    val tunnelSessionRepository = TunnelSessionRepository()
    val tunnelController = TunnelController(appContext)
}
