package ninja.mako.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class NetworkMonitor(context: Context) {
  private val connectivityManager =
    context.applicationContext.getSystemService(ConnectivityManager::class.java)

  fun snapshots(): Flow<NetworkSnapshot> = callbackFlow {
    fun publish() {
      trySend(buildSnapshot())
    }

    val callback = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) = publish()

      override fun onLost(network: Network) = publish()

      override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = publish()

      override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = publish()
    }

    publish()
    connectivityManager.registerDefaultNetworkCallback(callback)

    awaitClose {
      connectivityManager.unregisterNetworkCallback(callback)
    }
  }.distinctUntilChanged()

  private fun buildSnapshot(): NetworkSnapshot {
    val activeNetwork = connectivityManager.activeNetwork ?: return NetworkSnapshot()
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
    val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
    val bestAddress = selectBestAddress(linkProperties?.linkAddresses.orEmpty())

    return NetworkSnapshot(
      connected = capabilities != null,
      isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
      transportLabel = describeTransport(capabilities),
      interfaceName = linkProperties?.interfaceName,
      localAddress = bestAddress?.address?.hostAddress,
      subnet = bestAddress?.let { "${it.address.hostAddress}/${it.prefixLength}" },
      gateway = linkProperties
        ?.routes
        ?.firstOrNull { route -> route.gateway != null }
        ?.gateway
        ?.hostAddress,
      dnsServers = linkProperties?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty(),
      domains = linkProperties?.domains?.takeUnless { it.isBlank() },
      privateDnsServerName =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) linkProperties?.privateDnsServerName else null,
      isMetered = connectivityManager.isActiveNetworkMetered,
      isValidated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
      hasCaptivePortal = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true,
      hasVpnTransport = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    )
  }

  private fun describeTransport(capabilities: NetworkCapabilities?): String {
    if (capabilities == null) return "Unavailable"
    return when {
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
      else -> "Unknown"
    }
  }

  private fun selectBestAddress(addresses: List<LinkAddress>): LinkAddress? {
    return addresses
      .filterNot { address -> address.address.isLoopbackAddress || address.address.isLinkLocalAddress }
      .sortedBy { address -> if (address.address.hostAddress?.contains(':') == true) 1 else 0 }
      .firstOrNull()
  }
}
