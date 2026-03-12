package ninja.mako.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ninja.mako.discovery.ClassificationConfidence
import ninja.mako.discovery.DeviceClassification
import ninja.mako.discovery.HostEnrichment
import ninja.mako.discovery.HostProbeOutcome
import ninja.mako.discovery.HostProbeResult
import ninja.mako.network.NetworkSnapshot

class DetectedDeviceInventoryBuilderTest {
  @Test
  fun includesTheCurrentPhoneWhenWifiIsActive() {
    val snapshot = NetworkSnapshot(
      connected = true,
      isWifi = true,
      transportLabel = "Wi-Fi",
      interfaceName = "wlan0",
      localAddress = "192.168.1.44",
      subnet = "192.168.1.0/24"
    )

    val devices = DetectedDeviceInventoryBuilder.build(
      snapshot = snapshot,
      networkKey = "network-1",
      results = emptyList(),
      now = 1_000L
    )

    assertEquals(1, devices.size)
    assertEquals("This phone", devices.single().displayTitle)
    assertEquals("192.168.1.44", devices.single().hostAddress)
    assertTrue(devices.single().isLocalDevice)
    assertTrue(devices.single().detail.report.contains("This is the Android device running MAKO"))
  }

  @Test
  fun deduplicatesHostsAndKeepsTheLatestResponsiveObservation() {
    val snapshot = NetworkSnapshot(
      connected = true,
      isWifi = true,
      transportLabel = "Wi-Fi",
      localAddress = "192.168.1.44",
      gateway = "192.168.1.1",
      dnsServers = listOf("192.168.1.53")
    )
    val devices = DetectedDeviceInventoryBuilder.build(
      snapshot = snapshot,
      networkKey = "network-1",
      results = listOf(
        HostProbeResult(host = "192.168.1.10", outcome = HostProbeOutcome.CONNECTED, port = 80, observedAt = 100L),
        HostProbeResult(host = "192.168.1.10", outcome = HostProbeOutcome.REFUSED, port = 445, observedAt = 200L),
        HostProbeResult(host = "192.168.1.1", outcome = HostProbeOutcome.CONNECTED, port = 443, observedAt = 300L),
        HostProbeResult(host = "192.168.1.53", outcome = HostProbeOutcome.REFUSED, port = 53, observedAt = 400L),
        HostProbeResult(host = "192.168.1.20", outcome = HostProbeOutcome.TIMEOUT, port = 80, observedAt = 450L)
      ),
      now = 1_000L
    )

    assertEquals(4, devices.size)

    val peer = devices.first { device -> device.hostAddress == "192.168.1.10" }
    val gateway = devices.first { device -> device.hostAddress == "192.168.1.1" }
    val resolver = devices.first { device -> device.hostAddress == "192.168.1.53" }

    assertEquals("SMB", peer.badgeLabel)
    assertEquals("TCP 445 refused a connection", peer.statusLine)
    assertEquals("Gateway", gateway.badgeLabel)
    assertEquals("Resolver", resolver.badgeLabel)
    assertFalse(devices.any { device -> device.hostAddress == "192.168.1.20" })
  }

  @Test
  fun usesHostEnrichmentForTitlesAndDeviceDetails() {
    val snapshot = NetworkSnapshot(
      connected = true,
      isWifi = true,
      transportLabel = "Wi-Fi",
      localAddress = "192.168.1.44"
    )

    val devices = DetectedDeviceInventoryBuilder.build(
      snapshot = snapshot,
      networkKey = "network-1",
      results = listOf(
        HostProbeResult(
          host = "192.168.1.77",
          outcome = HostProbeOutcome.CONNECTED,
          port = 445,
          observedAt = 500L
        )
      ),
      enrichments = mapOf(
        "192.168.1.77" to HostEnrichment(
          hostname = "diskstation.office",
          macAddress = "00:11:22:33:44:55",
          manufacturer = "Synology",
          classification = DeviceClassification(
            label = "NAS / file server",
            badgeLabel = "NAS",
            confidence = ClassificationConfidence.HIGH,
            evidence = listOf("Hostname looks NAS-related.")
          )
        )
      ),
      now = 1_000L
    )

    val nas = devices.first { device -> device.hostAddress == "192.168.1.77" }
    assertEquals("diskstation", nas.displayTitle)
    assertEquals("NAS", nas.badgeLabel)
    assertTrue(nas.metaLine.contains("Synology"))
    assertTrue(nas.statusLine.contains("NAS / file server"))
    assertTrue(nas.detail.report.contains("Manufacturer: Synology"))
    assertTrue(nas.detail.report.contains("MAC address: 00:11:22:33:44:55"))
  }
}
