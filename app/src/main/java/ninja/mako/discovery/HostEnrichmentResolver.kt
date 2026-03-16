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

    var bannerServer: String? = null
    var bannerTitle: String? = null
    var hasHttpBanner = false
    
    // Attempt an HTTP/HTTPS banner grab if it proved reachable or open on any port
    if (result.outcome == HostProbeOutcome.CONNECTED || result.outcome == HostProbeOutcome.REFUSED) {
       val banner80 = HttpBannerGrabber.grab(result.host, 80)
       val banner443 = HttpBannerGrabber.grab(result.host, 443)
       
       bannerServer = banner80?.server ?: banner443?.server
       bannerTitle = banner80?.title ?: banner443?.title
       hasHttpBanner = banner80 != null || banner443 != null
    }

    val classification = DeviceClassifier.classify(snapshot, result, hostname, manufacturer)

    return HostEnrichment(
      hostname = hostname,
      macAddress = macAddress,
      manufacturer = manufacturer,
      httpServer = bannerServer,
      httpTitle = bannerTitle,
      classification = classification,
      evidenceSources = buildList {
        if (hostname != null) add("Reverse DNS")
        if (macAddress != null) add("Neighbor cache")
        if (manufacturer != null) add("IEEE OUI")
        if (hasHttpBanner) add("HTTP Banner")
      }
    )
  }

  companion object {
    private const val REVERSE_DNS_TIMEOUT_MS = 750L
  }
}
