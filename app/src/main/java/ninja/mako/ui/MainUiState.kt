package ninja.mako.ui

import ninja.mako.network.NetworkSnapshot

data class MainUiState(
  val eyebrow: String = "Awaiting Wi-Fi link",
  val headline: String = "No active network",
  val subhead: String = "Connect to Wi-Fi to start local discovery and per-network memory.",
  val statusBadge: String = "Offline",
  val transport: String = "Unavailable",
  val interfaceName: String = "Unavailable",
  val localAddress: String = "Unavailable",
  val subnet: String = "Unavailable",
  val gateway: String = "Unavailable",
  val dnsServers: String = "Unavailable",
  val domains: String = "Unavailable",
  val privateDns: String = "Off / not advertised",
  val validation: String = "No active validation state",
  val discoverySummary: String = "Discovery stack scaffolded. Subnet sweeps, PTR, mDNS, SSDP, and service fingerprinting land next.",
  val networkMemorySummary: String = "No active Wi-Fi network means no current network record to attach history to yet.",
  val wifiWarning: String = "MAKO only builds network inventory while connected to Wi-Fi.",
  val showWifiWarning: Boolean = true
) {
  companion object {
    fun from(snapshot: NetworkSnapshot): MainUiState {
      if (!snapshot.connected) return MainUiState()

      val validationBits = buildList {
        if (snapshot.isValidated) add("Validated internet")
        if (snapshot.hasCaptivePortal) add("Captive portal")
        add(if (snapshot.isMetered) "Metered" else "Unmetered")
        if (snapshot.hasVpnTransport) add("VPN transport")
      }.joinToString(" · ")

      return if (snapshot.isWifi) {
        MainUiState(
          eyebrow = "Current water: Wi-Fi",
          headline = "Wi-Fi transport active",
          subhead = "MAKO can already see the active link context. The next layer binds device discovery, fingerprints, and timeline memory to this network.",
          statusBadge = when {
            snapshot.hasCaptivePortal -> "Captive portal"
            snapshot.isValidated -> "Wi-Fi online"
            else -> "Wi-Fi connected"
          },
          transport = snapshot.transportLabel,
          interfaceName = snapshot.interfaceName ?: "Unavailable",
          localAddress = snapshot.localAddress ?: "Unavailable",
          subnet = snapshot.subnet ?: "Unavailable",
          gateway = snapshot.gateway ?: "Unavailable",
          dnsServers = snapshot.dnsServers.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Unavailable",
          domains = snapshot.domains ?: "Unavailable",
          privateDns = snapshot.privateDnsServerName ?: "Off / not advertised",
          validation = validationBits,
          discoverySummary = "Planned stack: bounded subnet sweep, PTR lookups, mDNS, SSDP, and safe local banner fingerprinting.",
          networkMemorySummary = "This active Wi-Fi link is the future root record for per-network device memory, return visits, and timeline diffs.",
          wifiWarning = "",
          showWifiWarning = false
        )
      } else {
        MainUiState(
          eyebrow = "Waiting for Wi-Fi",
          headline = "${snapshot.transportLabel} network active",
          subhead = "MAKO is built for local Wi-Fi situational awareness. Connect to Wi-Fi to start local subnet discovery and per-network memory.",
          statusBadge = "${snapshot.transportLabel} only",
          transport = snapshot.transportLabel,
          interfaceName = snapshot.interfaceName ?: "Unavailable",
          localAddress = snapshot.localAddress ?: "Unavailable",
          subnet = snapshot.subnet ?: "Unavailable",
          gateway = snapshot.gateway ?: "Unavailable",
          dnsServers = snapshot.dnsServers.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Unavailable",
          domains = snapshot.domains ?: "Unavailable",
          privateDns = snapshot.privateDnsServerName ?: "Off / not advertised",
          validation = validationBits,
          networkMemorySummary = "No per-network Wi-Fi record is active because the current transport is not Wi-Fi.",
          wifiWarning = "MAKO is intentionally idle for local-network discovery until the device is on Wi-Fi.",
          showWifiWarning = true
        )
      }
    }
  }
}
