package ninja.mako.ui

import java.text.DateFormat
import java.util.Date
import ninja.mako.discovery.ClassificationConfidence
import ninja.mako.discovery.DeviceClassification
import ninja.mako.discovery.HostEnrichment
import ninja.mako.discovery.HostProbeOutcome
import ninja.mako.discovery.HostProbeResult
import ninja.mako.network.NetworkSnapshot

object DetectedDeviceInventoryBuilder {
  fun build(
    snapshot: NetworkSnapshot,
    networkKey: String?,
    results: List<HostProbeResult>,
    enrichments: Map<String, HostEnrichment> = emptyMap(),
    now: Long = System.currentTimeMillis()
  ): List<DiscoveredDeviceListItem> {
    val inventory = linkedMapOf<String, DiscoveredDeviceListItem>()

    buildLocalDevice(snapshot, networkKey, now)?.let { localDevice ->
      inventory[localDevice.deviceKey] = localDevice
    }

    val stableNetworkKey = networkKey ?: "active-network"
    results
      .asReversed()
      .asSequence()
      .filter(::isResponsive)
      .filterNot { result -> result.host == snapshot.localAddress }
      .map { result ->
        buildObservedDevice(
          snapshot = snapshot,
          networkKey = stableNetworkKey,
          result = result,
          enrichment = enrichments[result.host],
          now = now
        )
      }
      .forEach { device ->
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
      detail = DiscoveredDeviceDetail(
        title = "This phone",
        hostAddress = localAddress,
        badgeLabel = "Local",
        metaLine = metaLine.ifBlank { "Android device on the active Wi-Fi network" },
        statusLine = "Connected to the active Wi-Fi network",
        report = buildString {
          appendLine("Device detail")
          appendLine("Title: This phone")
          appendLine("Classification: Phone / tablet (High confidence)")
          appendLine("Host address: $localAddress")
          appendLine("Badge: Local")
          appendLine("Last seen: ${formatTimestamp(now)}")
          appendLine()
          appendLine("Evidence")
          appendLine("- This is the Android device running MAKO.")
          snapshot.interfaceName?.let { appendLine("- Interface: $it") }
          snapshot.subnet?.let { appendLine("- Active subnet: $it") }
          appendLine()
          appendLine("Network status")
          appendLine("- Connected to the active Wi-Fi network.")
        }.trimEnd()
      ),
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
    enrichment: HostEnrichment?,
    now: Long
  ): DiscoveredDeviceListItem {
    val classification = enrichment?.classification ?: defaultClassification(snapshot, result)
    val hostname = enrichment?.hostname?.let(::normalizeHostname)
    val displayTitle = buildDisplayTitle(result.host, hostname, enrichment?.manufacturer, classification)
    val identityHints = buildList {
      if (result.host == snapshot.gateway) add("Gateway")
      if (snapshot.dnsServers.contains(result.host)) add("DNS resolver")
      hostname?.let { add("PTR $it") }
      enrichment?.manufacturer?.let { add(it) }
      enrichment?.macAddress?.let { add("MAC $it") }
      when (result.port) {
        53 -> add("DNS service")
        80 -> add("Web interface")
        443 -> add("HTTPS endpoint")
        445 -> add("File sharing")
        631 -> add("Printer service")
      }
    }

    val badgeLabel = when {
      classification.badgeLabel != DeviceClassification.unknown().badgeLabel -> classification.badgeLabel
      result.host == snapshot.gateway -> "Gateway"
      snapshot.dnsServers.contains(result.host) -> "Resolver"
      result.port == 631 -> "Printer"
      result.port == 445 -> "SMB"
      result.port == 80 || result.port == 443 -> "Web UI"
      result.port == 53 -> "DNS"
      result.outcome == HostProbeOutcome.CONNECTED -> "Open service"
      else -> "Host alive"
    }

    val probeStatus = when (result.outcome) {
      HostProbeOutcome.CONNECTED -> "TCP ${result.port ?: "?"} accepted a connection"
      HostProbeOutcome.REFUSED -> "TCP ${result.port ?: "?"} refused a connection"
      HostProbeOutcome.TIMEOUT -> "Timed out"
      HostProbeOutcome.UNREACHABLE -> "Unreachable"
      HostProbeOutcome.FAILED -> "Probe failed"
    }
    val statusLine = buildString {
      append(probeStatus)
      if (classification.label != DeviceClassification.unknown().label) {
        append(" • ${classification.confidence.label()} confidence ${classification.label}")
      }
    }

    val metaParts = buildList {
      if (classification.label != DeviceClassification.unknown().label) {
        add(classification.label)
      }
      identityHints.distinct()
        .filterNot { hint -> hint == "MAC ${enrichment?.macAddress}" }
        .forEach(::add)
      add("Seen ${formatTimeAgo(result.observedAt, now)}")
    }
    val report = buildDetailReport(
      title = displayTitle,
      host = result.host,
      badgeLabel = badgeLabel,
      probeStatus = probeStatus,
      classification = classification,
      enrichment = enrichment,
      identityHints = identityHints.distinct(),
      observedAt = result.observedAt,
      now = now
    )

    return DiscoveredDeviceListItem(
      deviceKey = "$networkKey:${result.host}",
      displayTitle = displayTitle,
      hostAddress = result.host,
      badgeLabel = badgeLabel,
      metaLine = metaParts.joinToString(" • "),
      statusLine = statusLine,
      detail = DiscoveredDeviceDetail(
        title = displayTitle,
        hostAddress = result.host,
        badgeLabel = badgeLabel,
        metaLine = metaParts.joinToString(" • "),
        statusLine = statusLine,
        report = report
      ),
      searchText = buildList {
        add(result.host)
        hostname?.let(::add)
        enrichment?.manufacturer?.let(::add)
        enrichment?.macAddress?.let(::add)
        add(classification.label)
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

  private fun buildDisplayTitle(
    host: String,
    hostname: String?,
    manufacturer: String?,
    classification: DeviceClassification
  ): String {
    return when {
      !hostname.isNullOrBlank() -> hostname.substringBefore(".")
      !manufacturer.isNullOrBlank() && classification.label != DeviceClassification.unknown().label -> {
        "$manufacturer ${classification.badgeLabel}"
      }
      !manufacturer.isNullOrBlank() -> manufacturer
      classification.confidence != ClassificationConfidence.LOW &&
        classification.label != DeviceClassification.unknown().label -> classification.label
      else -> host
    }
  }

  private fun defaultClassification(
    snapshot: NetworkSnapshot,
    result: HostProbeResult
  ): DeviceClassification {
    return when {
      result.host == snapshot.gateway -> DeviceClassification(
        label = "Router / access point",
        badgeLabel = "Gateway",
        confidence = ClassificationConfidence.HIGH,
        evidence = listOf("Host matches the active network gateway.")
      )
      snapshot.dnsServers.contains(result.host) -> DeviceClassification(
        label = "Resolver / infrastructure",
        badgeLabel = "Resolver",
        confidence = ClassificationConfidence.MEDIUM,
        evidence = listOf("Host is one of the configured DNS resolvers.")
      )
      else -> DeviceClassification.unknown()
    }
  }

  private fun normalizeHostname(value: String): String {
    return value.trim().trimEnd('.')
  }

  private fun buildDetailReport(
    title: String,
    host: String,
    badgeLabel: String,
    probeStatus: String,
    classification: DeviceClassification,
    enrichment: HostEnrichment?,
    identityHints: List<String>,
    observedAt: Long,
    now: Long
  ): String {
    return buildString {
      appendLine("Device detail")
      appendLine("Title: $title")
      appendLine("Host address: $host")
      appendLine("Badge: $badgeLabel")
      appendLine("Classification: ${classification.label} (${classification.confidence.label()} confidence)")
      appendLine("Last seen: ${formatTimestamp(observedAt)}")
      appendLine("Relative time: ${formatTimeAgo(observedAt, now)}")
      appendLine("Probe status: $probeStatus")
      appendLine("Hostname: ${enrichment?.hostname ?: "Unavailable"}")
      appendLine("MAC address: ${enrichment?.macAddress ?: "Unavailable"}")
      appendLine("Manufacturer: ${enrichment?.manufacturer ?: "Unavailable"}")
      appendLine("Evidence sources: ${enrichment?.evidenceSources?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Observed sweep only"}")
      appendLine()
      appendLine("Classification evidence")
      classification.evidence.ifEmpty {
        listOf("No stronger classification evidence was available.")
      }.forEach { evidence ->
        appendLine("- $evidence")
      }
      appendLine()
      appendLine("Identity hints")
      identityHints.ifEmpty {
        listOf("No additional hostname, vendor, or service hints were available.")
      }.forEach { hint ->
        appendLine("- $hint")
      }
    }.trimEnd()
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

  private fun formatTimestamp(timestamp: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
  }
}
