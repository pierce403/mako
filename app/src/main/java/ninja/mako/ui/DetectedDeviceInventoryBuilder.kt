package ninja.mako.ui

import ninja.mako.discovery.HostProbeOutcome
import ninja.mako.discovery.HostProbeResult
import ninja.mako.discovery.HostSweepSession
import ninja.mako.network.NetworkSnapshot

object DetectedDeviceInventoryBuilder {
  fun build(
    snapshot: NetworkSnapshot,
    networkKey: String?,
    session: HostSweepSession?,
    now: Long = System.currentTimeMillis()
  ): List<DiscoveredDeviceListItem> {
    val inventory = linkedMapOf<String, DiscoveredDeviceListItem>()

    buildLocalDevice(snapshot, networkKey, now)?.let { localDevice ->
      inventory[localDevice.deviceKey] = localDevice
    }

    val stableNetworkKey = session?.networkKey ?: networkKey ?: "active-network"
    session
      ?.results
      ?.asReversed()
      ?.asSequence()
      ?.filter(::isResponsive)
      ?.filterNot { result -> result.host == snapshot.localAddress }
      ?.map { result -> buildObservedDevice(snapshot, stableNetworkKey, result, now) }
      ?.forEach { device ->
        inventory.putIfAbsent(device.deviceKey, device)
      }

    return inventory.values.toList()
  }

  private fun buildLocalDevice(
    snapshot: NetworkSnapshot,
    networkKey: String?,
    now: Long
  ): DiscoveredDeviceListItem? {
    if (!snapshot.connected || !snapshot.isWifi) return null

    val localAddress = snapshot.localAddress ?: return null
    val keyPrefix = networkKey ?: "active-network"
    val metaLine = buildList {
      add("Android device")
      snapshot.interfaceName?.let(::add)
      snapshot.subnet?.let { subnet -> add("On $subnet") }
    }.joinToString(" • ")

    return DiscoveredDeviceListItem(
      deviceKey = "$keyPrefix:self:$localAddress",
      displayTitle = "This phone",
      hostAddress = localAddress,
      badgeLabel = "Local",
      metaLine = metaLine.ifBlank { "Android device on the active Wi-Fi network" },
      statusLine = "Connected to the active Wi-Fi network",
      searchText = buildString {
        appendLine("This phone")
        appendLine(localAddress)
        appendLine("Local device")
        snapshot.interfaceName?.let(::appendLine)
        snapshot.subnet?.let(::appendLine)
      }.trim(),
      sortTimestamp = now,
      lastSeen = now,
      isOpenService = false,
      isInfrastructure = false,
      isWebInterface = false,
      isLocalDevice = true
    )
  }

  private fun buildObservedDevice(
    snapshot: NetworkSnapshot,
    networkKey: String,
    result: HostProbeResult,
    now: Long
  ): DiscoveredDeviceListItem {
    val identityHints = buildList {
      if (result.host == snapshot.gateway) add("Gateway")
      if (snapshot.dnsServers.contains(result.host)) add("DNS resolver")
      when (result.port) {
        53 -> add("DNS service")
        80 -> add("Web interface")
        443 -> add("HTTPS endpoint")
        445 -> add("File sharing")
        631 -> add("Printer service")
      }
    }

    val badgeLabel = when {
      result.host == snapshot.gateway -> "Gateway"
      snapshot.dnsServers.contains(result.host) -> "Resolver"
      result.port == 631 -> "Printer"
      result.port == 445 -> "SMB"
      result.port == 80 || result.port == 443 -> "Web UI"
      result.port == 53 -> "DNS"
      result.outcome == HostProbeOutcome.CONNECTED -> "Open service"
      else -> "Host alive"
    }

    val statusLine = when (result.outcome) {
      HostProbeOutcome.CONNECTED -> "TCP ${result.port ?: "?"} accepted a connection"
      HostProbeOutcome.REFUSED -> "TCP ${result.port ?: "?"} refused a connection"
      HostProbeOutcome.TIMEOUT -> "Timed out"
      HostProbeOutcome.UNREACHABLE -> "Unreachable"
      HostProbeOutcome.FAILED -> "Probe failed"
    }

    val metaParts = buildList {
      addAll(identityHints.distinct())
      add("Seen ${formatTimeAgo(result.observedAt, now)}")
    }

    return DiscoveredDeviceListItem(
      deviceKey = "$networkKey:${result.host}",
      displayTitle = result.host,
      hostAddress = result.host,
      badgeLabel = badgeLabel,
      metaLine = metaParts.joinToString(" • "),
      statusLine = statusLine,
      searchText = buildList {
        add(result.host)
        add(badgeLabel)
        add(statusLine)
        addAll(identityHints)
      }.joinToString("\n"),
      sortTimestamp = result.observedAt,
      lastSeen = result.observedAt,
      isOpenService = result.outcome == HostProbeOutcome.CONNECTED,
      isInfrastructure = result.host == snapshot.gateway || snapshot.dnsServers.contains(result.host),
      isWebInterface = result.port == 80 || result.port == 443,
      isLocalDevice = false
    )
  }

  private fun isResponsive(result: HostProbeResult): Boolean {
    return result.outcome == HostProbeOutcome.CONNECTED || result.outcome == HostProbeOutcome.REFUSED
  }

  private fun formatTimeAgo(timestamp: Long, now: Long): String {
    val deltaSeconds = ((now - timestamp).coerceAtLeast(0L)) / 1_000L
    return when {
      deltaSeconds < 5L -> "just now"
      deltaSeconds < 60L -> "${deltaSeconds}s ago"
      deltaSeconds < 3_600L -> "${deltaSeconds / 60L}m ago"
      else -> "${deltaSeconds / 3_600L}h ago"
    }
  }
}
