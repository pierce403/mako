package ninja.mako.ui

import java.text.DateFormat
import java.util.Date
import ninja.mako.BuildConfig
import ninja.mako.data.NetworkRecordEntity
import ninja.mako.discovery.HostCandidatePlanner
import ninja.mako.discovery.HostDiscoveryPlan
import ninja.mako.discovery.HostSweepSession
import ninja.mako.discovery.HostSweepStatus
import ninja.mako.network.NetworkSnapshot

data class MainUiState(
  val eyebrow: String = "Inventory status",
  val headline: String = "Wi-Fi inventory unavailable",
  val subhead: String = "Connect to Wi-Fi to start local device discovery and per-network memory.",
  val statusBadge: String = "Offline",
  val scanEnabled: Boolean = true,
  val sweepStatus: HostSweepStatus? = null,
  val totalDetectedDevices: Int = 0,
  val transport: String = "Unavailable",
  val interfaceName: String = "Unavailable",
  val localAddress: String = "Unavailable",
  val subnet: String = "Unavailable",
  val gateway: String = "Unavailable",
  val dnsServers: String = "Unavailable",
  val domains: String = "Unavailable",
  val privateDns: String = "Off / not advertised",
  val validation: String = "No active validation state",
  val discoverySummary: String = "MAKO starts local discovery once an active Wi-Fi link is available.",
  val networkMemorySummary: String = "Per-network history appears here once MAKO has an active Wi-Fi record.",
  val diagnosticsReport: String = "No active diagnostics report.",
  val wifiWarning: String = "MAKO only builds network inventory while connected to Wi-Fi.",
  val showWifiWarning: Boolean = true
) {
  companion object {
    fun from(
      snapshot: NetworkSnapshot,
      record: NetworkRecordEntity?,
      knownNetworkCount: Int,
      discoveryPlan: HostDiscoveryPlan?,
      sweepSession: HostSweepSession?,
      scanEnabled: Boolean,
      inventoryDeviceCount: Int
    ): MainUiState {
      if (!snapshot.connected) {
        return MainUiState(
          scanEnabled = scanEnabled,
          sweepStatus = sweepSession?.status,
          totalDetectedDevices = inventoryDeviceCount,
          diagnosticsReport = buildDiagnosticsReport(
            snapshot = snapshot,
            record = record,
            knownNetworkCount = knownNetworkCount,
            discoveryPlan = discoveryPlan,
            sweepSession = sweepSession,
            scanEnabled = scanEnabled
          )
        )
      }

      val validationBits = buildList {
        if (snapshot.isValidated) add("Validated internet")
        if (snapshot.hasCaptivePortal) add("Captive portal")
        add(if (snapshot.isMetered) "Metered" else "Unmetered")
        if (snapshot.hasVpnTransport) add("VPN transport")
      }.joinToString(" · ")

      return if (snapshot.isWifi) {
        val knownNetwork = record != null && record.activationCount > 1
        val firstSeen = record?.firstSeenAt?.let(::formatTimestamp)

        MainUiState(
          eyebrow = "Current water: Wi-Fi",
          headline = "Local Wi-Fi inventory",
          subhead = if (record == null) {
            "Preparing the first on-device record for this Wi-Fi network."
          } else if (knownNetwork) {
            "Restored this network's local inventory and history."
          } else {
            "Started a fresh local inventory for this Wi-Fi network."
          },
          statusBadge = when {
            !scanEnabled -> "Scan paused"
            snapshot.hasCaptivePortal -> "Captive portal"
            knownNetwork -> "Known Wi-Fi"
            snapshot.isValidated -> "New Wi-Fi"
            else -> "Wi-Fi connected"
          },
          scanEnabled = scanEnabled,
          sweepStatus = sweepSession?.status,
          totalDetectedDevices = inventoryDeviceCount,
          transport = snapshot.transportLabel,
          interfaceName = snapshot.interfaceName ?: "Unavailable",
          localAddress = snapshot.localAddress ?: "Unavailable",
          subnet = snapshot.subnet ?: "Unavailable",
          gateway = snapshot.gateway ?: "Unavailable",
          dnsServers = snapshot.dnsServers.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Unavailable",
          domains = snapshot.domains ?: "Unavailable",
          privateDns = snapshot.privateDnsServerName ?: "Off / not advertised",
          validation = validationBits,
          discoverySummary = when {
            !scanEnabled -> pausedDiscoverySummary(discoveryPlan, sweepSession)
            sweepSession != null && sweepSession.status == HostSweepStatus.RUNNING -> {
              "Sweep running on ${sweepSession.subnetCidr}: ${sweepSession.hostsAttempted}/${sweepSession.hostsPlanned} hosts checked, ${sweepSession.reachableHosts} responsive so far."
            }
            sweepSession != null && sweepSession.status == HostSweepStatus.COMPLETED -> {
              "Last sweep on ${sweepSession.subnetCidr}: ${sweepSession.reachableHosts} responsive hosts. Use Rescan for another pass or Stop scan to pause."
            }
            sweepSession != null && sweepSession.status == HostSweepStatus.CANCELLED -> {
              "The last sweep stopped because the active network changed."
            }
            sweepSession != null && sweepSession.status == HostSweepStatus.FAILED -> {
              "The last sweep failed before MAKO could finish the local inventory pass."
            }
            discoveryPlan != null -> {
              buildString {
                append("Planned sweep: ${discoveryPlan.candidateHosts.size} hosts on ${discoveryPlan.subnetCidr}.")
                append(" Prioritizing the gateway, resolvers, and nearby addresses.")
                append(" Rescan advances to the next bounded slice.")
                append(if (discoveryPlan.truncated) " Bounded for civility." else " All usable hosts fit the current budget.")
              }
            }
            else -> "Waiting for the first bounded local subnet sweep plan."
          },
          networkMemorySummary = if (record == null) {
            "Preparing a new on-device record. Known Wi-Fi networks stored locally: $knownNetworkCount."
          } else {
            buildString {
              append(if (knownNetwork) "Known network record." else "New network record.")
              append(" First seen ${firstSeen ?: "just now"}.")
              append(" Sessions: ${record.activationCount}.")
              append(" Local records: $knownNetworkCount.")
            }
          },
          diagnosticsReport = buildDiagnosticsReport(
            snapshot = snapshot,
            record = record,
            knownNetworkCount = knownNetworkCount,
            discoveryPlan = discoveryPlan,
            sweepSession = sweepSession,
            scanEnabled = scanEnabled
          ),
          wifiWarning = "",
          showWifiWarning = false
        )
      } else {
        MainUiState(
          eyebrow = "Waiting for Wi-Fi",
          headline = "Wi-Fi inventory unavailable",
          subhead = "The current transport is ${snapshot.transportLabel}. Connect to Wi-Fi to inventory local devices.",
          statusBadge = "${snapshot.transportLabel} only",
          scanEnabled = scanEnabled,
          sweepStatus = sweepSession?.status,
          totalDetectedDevices = inventoryDeviceCount,
          transport = snapshot.transportLabel,
          interfaceName = snapshot.interfaceName ?: "Unavailable",
          localAddress = snapshot.localAddress ?: "Unavailable",
          subnet = snapshot.subnet ?: "Unavailable",
          gateway = snapshot.gateway ?: "Unavailable",
          dnsServers = snapshot.dnsServers.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Unavailable",
          domains = snapshot.domains ?: "Unavailable",
          privateDns = snapshot.privateDnsServerName ?: "Off / not advertised",
          validation = validationBits,
          discoverySummary = "MAKO is idle because local-network discovery only runs on Wi-Fi.",
          networkMemorySummary = "No active Wi-Fi record. Known Wi-Fi networks stored locally: $knownNetworkCount.",
          diagnosticsReport = buildDiagnosticsReport(
            snapshot = snapshot,
            record = record,
            knownNetworkCount = knownNetworkCount,
            discoveryPlan = discoveryPlan,
            sweepSession = sweepSession,
            scanEnabled = scanEnabled
          ),
          wifiWarning = "MAKO is intentionally idle for local-network discovery until the device is on Wi-Fi.",
          showWifiWarning = true
        )
      }
    }

    private fun pausedDiscoverySummary(
      discoveryPlan: HostDiscoveryPlan?,
      sweepSession: HostSweepSession?
    ): String {
      return when {
        sweepSession != null && sweepSession.status == HostSweepStatus.COMPLETED -> {
          "Discovery paused after the last sweep on ${sweepSession.subnetCidr}. Use Start scan from the menu to resume."
        }
        discoveryPlan != null -> {
          "Discovery paused. Planned sweep: ${discoveryPlan.candidateHosts.size} hosts on ${discoveryPlan.subnetCidr}. Use Start scan from the menu to resume."
        }
        else -> "Discovery paused. Use Start scan from the menu once MAKO can plan the next local Wi-Fi sweep."
      }
    }

    private fun formatTimestamp(timestamp: Long): String {
      return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
    }

    private fun buildDiagnosticsReport(
      snapshot: NetworkSnapshot,
      record: NetworkRecordEntity?,
      knownNetworkCount: Int,
      discoveryPlan: HostDiscoveryPlan?,
      sweepSession: HostSweepSession?,
      scanEnabled: Boolean
    ): String {
      val permissionState =
        "Current app permissions: INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, CHANGE_WIFI_MULTICAST_STATE, FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC, and POST_NOTIFICATIONS on Android 13+ for user-visible continuous-scan alerts. SSID/BSSID collection is not enabled yet."

      val settingsState = buildString {
        append("Foreground scan enabled: $scanEnabled.")
        append("\nContinuous background scanning: explicit overflow-menu toggle backed by a foreground service.")
        append("\nHost plan budget: ${HostCandidatePlanner.DEFAULT_MAX_HOSTS} hosts.")
        append("\nTCP sweep ports: 53, 80, 443, 445, 631.")
        append("\nTCP sweep concurrency: 12 hosts.")
        append("\nTCP connect timeout: 250 ms.")
        append("\nManual rescan forces an immediate new sweep, even when the bounded host slice is unchanged.")
      }

      return buildString {
        appendLine("MAKO Diagnostics Report")
        appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Sensitive values included below: local IPs, gateway IP, DNS servers, and discovered host addresses.")
        appendLine()
        appendLine("Connectivity")
        appendLine("Transport: ${snapshot.transportLabel}")
        appendLine("Connected: ${snapshot.connected}")
        appendLine("Wi-Fi: ${snapshot.isWifi}")
        appendLine("Interface: ${snapshot.interfaceName ?: "Unavailable"}")
        appendLine("Local address: ${snapshot.localAddress ?: "Unavailable"}")
        appendLine("Subnet: ${snapshot.subnet ?: "Unavailable"}")
        appendLine("Gateway: ${snapshot.gateway ?: "Unavailable"}")
        appendLine("DNS servers: ${snapshot.dnsServers.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Unavailable"}")
        appendLine("Domains: ${snapshot.domains ?: "Unavailable"}")
        appendLine("Private DNS: ${snapshot.privateDnsServerName ?: "Off / not advertised"}")
        appendLine("Validated: ${snapshot.isValidated}")
        appendLine("Captive portal: ${snapshot.hasCaptivePortal}")
        appendLine("Metered: ${snapshot.isMetered}")
        appendLine("VPN transport: ${snapshot.hasVpnTransport}")
        appendLine()
        appendLine("Network Record")
        appendLine("Known network count: $knownNetworkCount")
        appendLine("Network key: ${record?.networkKey ?: "Unavailable"}")
        appendLine("Stable input summary: ${record?.stableInputSummary ?: "Unavailable"}")
        appendLine("First seen: ${record?.firstSeenAt?.let(::formatTimestamp) ?: "Unavailable"}")
        appendLine("Last connected: ${record?.lastConnectedAt?.let(::formatTimestamp) ?: "Unavailable"}")
        appendLine("Activation count: ${record?.activationCount ?: 0}")
        appendLine()
        appendLine("Discovery Plan")
        appendLine("Plan available: ${discoveryPlan != null}")
        appendLine("Plan subnet: ${discoveryPlan?.subnetCidr ?: "Unavailable"}")
        appendLine("Candidate hosts: ${discoveryPlan?.candidateHosts?.size ?: 0}")
        appendLine("Total usable hosts: ${discoveryPlan?.totalUsableHostCount ?: 0}")
        appendLine("Prioritized hosts: ${discoveryPlan?.prioritizedHosts?.take(12)?.joinToString(", ") ?: "Unavailable"}")
        appendLine("Plan truncated: ${discoveryPlan?.truncated ?: false}")
        appendLine()
        appendLine("Sweep Session")
        appendLine("Sweep status: ${sweepSession?.status ?: "Unavailable"}")
        appendLine("Sweep started: ${sweepSession?.startedAt?.let(::formatTimestamp) ?: "Unavailable"}")
        appendLine("Sweep ended: ${sweepSession?.endedAt?.let(::formatTimestamp) ?: "Unavailable"}")
        appendLine("Hosts planned: ${sweepSession?.hostsPlanned ?: 0}")
        appendLine("Hosts attempted: ${sweepSession?.hostsAttempted ?: 0}")
        appendLine("Reachable hosts: ${sweepSession?.reachableHosts ?: 0}")
        appendLine("Open-service hosts: ${sweepSession?.openServiceHosts ?: 0}")
        appendLine("Ports probed: ${sweepSession?.portsProbed?.joinToString(", ") ?: "Unavailable"}")
        appendLine("Reachable samples: ${sweepSession?.sampleReachableHosts?.joinToString(", ") ?: "Unavailable"}")
        appendLine()
        appendLine("Per-host probe results")
        if (sweepSession?.results.isNullOrEmpty()) {
          appendLine("No probe results captured yet.")
        } else {
          sweepSession?.results?.forEach { result ->
            appendLine("${result.host} -> ${result.outcome}${result.port?.let { port -> " on $port" } ?: ""}")
          }
        }
        appendLine()
        appendLine("Permission posture")
        appendLine(permissionState)
        appendLine()
        appendLine("Current settings")
        appendLine(settingsState)
      }.trimEnd()
    }
  }
}
