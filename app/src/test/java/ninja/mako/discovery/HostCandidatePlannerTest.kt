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
}
