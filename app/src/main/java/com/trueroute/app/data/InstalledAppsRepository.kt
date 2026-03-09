package com.trueroute.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.trueroute.app.model.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledAppsRepository(
    private val context: Context,
) {
    suspend fun loadInstalledApps(): List<InstalledApp> = withContext(Dispatchers.Default) {
        val packageManager = context.packageManager
        packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filterNot { it.packageName == context.packageName }
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .map { applicationInfo ->
                InstalledApp(
                    packageName = applicationInfo.packageName,
                    label = packageManager.getApplicationLabel(applicationInfo).toString(),
                )
            }
            .sortedWith(compareBy(InstalledApp::label, InstalledApp::packageName))
            .toList()
    }
}
