package com.ultimate.codecreator.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Abstraction over connectivity so routing logic can be unit tested. */
interface NetworkMonitor {
    fun isOnline(): Boolean
}

class AndroidNetworkMonitor(context: Context) : NetworkMonitor {
    private val manager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun isOnline(): Boolean = runCatching {
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)
}
