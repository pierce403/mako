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
import java.net.InetAddress

class NetworkMonitor(context: Context) {
  private val connectivityManager =
    context.applicationContext.getSystemService(ConnectivityManager::class.java)

  fun snapshots(): Flow<NetworkSnapshot> = callbackFlow {
    var state = initialState()

    fun updateNetwork(network: Network) {
      if (state.network != network) {
        state = ObservedNetworkState(network = network)
      }
    }

    fun publish() {
      trySend(buildSnapshot(state))
    }

    val callback = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        updateNetwork(network)
      }

      override fun onLost(network: Network) {
        if (state.network == network) {
          state = ObservedNetworkState()
          publish()
        }
      }

      override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
        updateNetwork(network)
        state = state.copy(capabilities = networkCapabilities)
        publish()
      }

      override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
        updateNetwork(network)
        state = state.copy(linkProperties = linkProperties)
        publish()
      }
    }

    publish()
    connectivityManager.registerDefaultNetworkCallback(callback)

    awaitClose {
      connectivityManager.unregisterNetworkCallback(callback)
    }
  }.distinctUntilChanged()

  private fun initialState(): ObservedNetworkState {
    val activeNetwork = connectivityManager.activeNetwork ?: return ObservedNetworkState()
    return ObservedNetworkState(
      network = activeNetwork,
      capabilities = connectivityManager.getNetworkCapabilities(activeNetwork),
      linkProperties = connectivityManager.getLinkProperties(activeNetwork)
    )
  }

  private fun buildSnapshot(state: ObservedNetworkState): NetworkSnapshot {
    val capabilities = state.capabilities
    val linkProperties = state.linkProperties
    val bestAddress = selectBestAddress(linkProperties?.linkAddresses.orEmpty())

    return NetworkSnapshot(
      connected = capabilities != null,
      isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
      transportLabel = describeTransport(capabilities),
      interfaceName = linkProperties?.interfaceName,
      localAddress = bestAddress?.address?.hostAddress,
      subnet = bestAddress?.let(::toNetworkCidr),
      gateway = linkProperties
        ?.routes
        ?.firstOrNull { route -> route.gateway != null }
        ?.gateway
        ?.hostAddress,
      dnsServers = linkProperties?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty(),
      domains = linkProperties?.domains?.takeUnless { it.isBlank() },
      privateDnsServerName =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) linkProperties?.privateDnsServerName else null,
      isMetered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false,
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

  private fun toNetworkCidr(linkAddress: LinkAddress): String {
    val maskedAddress = linkAddress.address.address.clone()
    var remainingBits = linkAddress.prefixLength

    maskedAddress.indices.forEach { index ->
      val mask = when {
        remainingBits >= 8 -> 0xFF
        remainingBits <= 0 -> 0x00
        else -> (0xFF shl (8 - remainingBits)) and 0xFF
      }
      maskedAddress[index] = (maskedAddress[index].toInt() and mask).toByte()
      remainingBits -= 8
    }

    val networkAddress = InetAddress.getByAddress(maskedAddress).hostAddress
    return "$networkAddress/${linkAddress.prefixLength}"
  }

  private data class ObservedNetworkState(
    val network: Network? = null,
    val capabilities: NetworkCapabilities? = null,
    val linkProperties: LinkProperties? = null
  )
}
