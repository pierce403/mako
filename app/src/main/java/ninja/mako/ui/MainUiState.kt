package ninja.mako.ui

import java.text.DateFormat
import java.util.Date
import ninja.mako.data.NetworkRecordEntity
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
    fun from(
      snapshot: NetworkSnapshot,
      record: NetworkRecordEntity?,
      knownNetworkCount: Int
    ): MainUiState {
      if (!snapshot.connected) return MainUiState()

      val validationBits = buildList {
        if (snapshot.isValidated) add("Validated internet")
        if (snapshot.hasCaptivePortal) add("Captive portal")
        add(if (snapshot.isMetered) "Metered" else "Unmetered")
        if (snapshot.hasVpnTransport) add("VPN transport")
      }.joinToString(" · ")

      return if (snapshot.isWifi) {
        val knownNetwork = record != null && record.activationCount > 1
        val firstSeen = record?.firstSeenAt?.let(::formatTimestamp)
        val lastConnected = record?.lastConnectedAt?.let(::formatTimestamp)
        val keySuffix = record?.networkKey ?: "pending"

        MainUiState(
          eyebrow = "Current water: Wi-Fi",
          headline = if (knownNetwork) "Known Wi-Fi network active" else "New Wi-Fi network active",
          subhead = if (record == null) {
            "MAKO can already see the active link context. The new per-network record is still materializing."
          } else if (knownNetwork) {
            "MAKO recognized this network and restored its local network record."
          } else {
            "MAKO created a fresh local network record for this Wi-Fi environment."
          },
          statusBadge = when {
            snapshot.hasCaptivePortal -> "Captive portal"
            knownNetwork -> "Known Wi-Fi"
            snapshot.isValidated -> "New Wi-Fi"
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
          networkMemorySummary = if (record == null) {
            "No persisted network record is attached yet. Known networks stored locally: $knownNetworkCount."
          } else {
            buildString {
              append(if (knownNetwork) "Known network record." else "New network record.")
              append(" Local count: $knownNetworkCount.")
              append(" First seen: ${firstSeen ?: "just now"}.")
              append(" Last connected: ${lastConnected ?: "just now"}.")
              append(" Sessions: ${record.activationCount}.")
              append(" Key: $keySuffix.")
              append(" Basis: ${record.stableInputSummary}.")
            }
          },
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

    private fun formatTimestamp(timestamp: Long): String {
      return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
    }
  }
}
