package ninja.mako.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ninja.mako.discovery.HostProbeOutcome
import ninja.mako.discovery.HostProbeResult
import ninja.mako.discovery.HostSweepSession
import ninja.mako.discovery.HostSweepStatus
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
      session = null,
      now = 1_000L
    )

    assertEquals(1, devices.size)
    assertEquals("This phone", devices.single().displayTitle)
    assertEquals("192.168.1.44", devices.single().hostAddress)
    assertTrue(devices.single().isLocalDevice)
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
    val session = HostSweepSession(
      networkKey = "network-1",
      subnetCidr = "192.168.1.0/24",
      status = HostSweepStatus.COMPLETED,
      startedAt = 0L,
      endedAt = 500L,
      hostsPlanned = 4,
      hostsAttempted = 4,
      reachableHosts = 3,
      openServiceHosts = 2,
      portsProbed = listOf(53, 80, 443, 445, 631),
      sampleReachableHosts = emptyList(),
      results = listOf(
        HostProbeResult(host = "192.168.1.10", outcome = HostProbeOutcome.CONNECTED, port = 80, observedAt = 100L),
        HostProbeResult(host = "192.168.1.10", outcome = HostProbeOutcome.REFUSED, port = 445, observedAt = 200L),
        HostProbeResult(host = "192.168.1.1", outcome = HostProbeOutcome.CONNECTED, port = 443, observedAt = 300L),
        HostProbeResult(host = "192.168.1.53", outcome = HostProbeOutcome.REFUSED, port = 53, observedAt = 400L),
        HostProbeResult(host = "192.168.1.20", outcome = HostProbeOutcome.TIMEOUT, port = 80, observedAt = 450L)
      )
    )

    val devices = DetectedDeviceInventoryBuilder.build(
      snapshot = snapshot,
      networkKey = "network-1",
      session = session,
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
}
