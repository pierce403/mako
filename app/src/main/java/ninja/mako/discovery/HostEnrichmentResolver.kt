package ninja.mako.discovery

import android.content.Context
import kotlinx.coroutines.withTimeoutOrNull
import ninja.mako.network.NetworkSnapshot

class HostEnrichmentResolver(
  private val context: Context
) {
  suspend fun resolve(
    snapshot: NetworkSnapshot,
    result: HostProbeResult
  ): HostEnrichment {
    val hostname = withTimeoutOrNull(REVERSE_DNS_TIMEOUT_MS) {
      ReverseDnsHostResolver.lookup(result.host)
    }

    val macAddress = NeighborTableReader.macAddressForHost(result.host)
    val manufacturer = macAddress?.let { address ->
      OuiVendorLookup.lookup(context, address)
    }
    val classification = DeviceClassifier.classify(snapshot, result, hostname, manufacturer)

    return HostEnrichment(
      hostname = hostname,
      macAddress = macAddress,
      manufacturer = manufacturer,
      classification = classification,
      evidenceSources = buildList {
        if (hostname != null) add("Reverse DNS")
        if (macAddress != null) add("Neighbor cache")
        if (manufacturer != null) add("IEEE OUI")
      }
    )
  }

  companion object {
    private const val REVERSE_DNS_TIMEOUT_MS = 750L
  }
}
