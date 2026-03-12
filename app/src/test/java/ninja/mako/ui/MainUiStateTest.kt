package ninja.mako.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ninja.mako.discovery.HostDiscoveryPlan
import ninja.mako.network.NetworkSnapshot

class MainUiStateTest {
  @Test
  fun reportsPausedDiscoveryWhenForegroundScanIsDisabled() {
    val state = MainUiState.from(
      snapshot = NetworkSnapshot(
        connected = true,
        isWifi = true,
        transportLabel = "Wi-Fi",
        localAddress = "192.168.1.44",
        subnet = "192.168.1.0/24",
        gateway = "192.168.1.1"
      ),
      record = null,
      knownNetworkCount = 0,
      discoveryPlan = HostDiscoveryPlan(
        subnetCidr = "192.168.1.0/24",
        candidateHosts = listOf("192.168.1.1", "192.168.1.2"),
        prioritizedHosts = listOf("192.168.1.1"),
        totalUsableHostCount = 254,
        truncated = true
      ),
      sweepSession = null,
      scanEnabled = false,
      inventoryDeviceCount = 0
    )

    assertEquals("Scan paused", state.statusBadge)
    assertTrue(state.discoverySummary.contains("Discovery paused"))
    assertTrue(state.diagnosticsReport.contains("Foreground scan enabled: false"))
  }
}
