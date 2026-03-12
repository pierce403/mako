package ninja.mako.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ninja.mako.network.NetworkSnapshot

class HostCandidatePlannerTest {
  @Test
  fun prioritizesGatewayDnsAndNeighborsWithinBoundedRange() {
    val snapshot = NetworkSnapshot(
      connected = true,
      isWifi = true,
      transportLabel = "Wi-Fi",
      localAddress = "192.168.1.44",
      subnet = "192.168.1.0/24",
      gateway = "192.168.1.1",
      dnsServers = listOf("1.1.1.1", "192.168.1.53")
    )

    val plan = HostCandidatePlanner.buildPlan(snapshot, maxHosts = 8)

    assertNotNull(plan)
    assertEquals(8, plan?.candidateHosts?.size)
    assertEquals("192.168.1.1", plan?.candidateHosts?.first())
    assertTrue(plan?.candidateHosts?.contains("192.168.1.53") == true)
    assertTrue(plan?.candidateHosts?.contains("192.168.1.43") == true)
    assertTrue(plan?.candidateHosts?.contains("192.168.1.45") == true)
    assertTrue(plan?.truncated == true)
  }

  @Test
  fun returnsNullForNonWifiOrMissingIpv4Subnet() {
    val snapshot = NetworkSnapshot(
      connected = true,
      isWifi = false,
      transportLabel = "Cellular",
      localAddress = "10.0.0.5",
      subnet = "10.0.0.0/24"
    )

    assertEquals(null, HostCandidatePlanner.buildPlan(snapshot))
    assertEquals(
      null,
      HostCandidatePlanner.buildPlan(
        snapshot.copy(isWifi = true, transportLabel = "Wi-Fi", subnet = "fe80::/64")
      )
    )
  }

  @Test
  fun rotatesRemainingHostsAcrossScanCycles() {
    val snapshot = NetworkSnapshot(
      connected = true,
      isWifi = true,
      transportLabel = "Wi-Fi",
      subnet = "192.168.1.0/24",
      gateway = "192.168.1.1",
      dnsServers = listOf("192.168.1.53")
    )

    val firstPlan = HostCandidatePlanner.buildPlan(snapshot, scanCycle = 0, maxHosts = 8)
    val secondPlan = HostCandidatePlanner.buildPlan(snapshot, scanCycle = 1, maxHosts = 8)

    assertNotNull(firstPlan)
    assertNotNull(secondPlan)
    assertEquals(listOf("192.168.1.1", "192.168.1.53"), firstPlan?.candidateHosts?.take(2))
    assertEquals(listOf("192.168.1.1", "192.168.1.53"), secondPlan?.candidateHosts?.take(2))
    assertEquals(listOf("192.168.1.2", "192.168.1.3", "192.168.1.4", "192.168.1.5", "192.168.1.6", "192.168.1.7"), firstPlan?.candidateHosts?.drop(2))
    assertEquals(listOf("192.168.1.8", "192.168.1.9", "192.168.1.10", "192.168.1.11", "192.168.1.12", "192.168.1.13"), secondPlan?.candidateHosts?.drop(2))
  }
}
