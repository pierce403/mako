package ninja.mako.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NetworkIdentityTest {
  @Test
  fun localAddressAndDnsOrderDoNotChangeNetworkKey() {
    val first = NetworkSnapshot(
      connected = true,
      isWifi = true,
      transportLabel = "Wi-Fi",
      localAddress = "192.168.1.44",
      subnet = "192.168.1.0/24",
      gateway = "192.168.1.1",
      dnsServers = listOf("192.168.1.1", "1.1.1.1"),
      domains = "lan"
    )
    val second = first.copy(
      localAddress = "192.168.1.88",
      dnsServers = listOf("1.1.1.1", "192.168.1.1")
    )

    val firstIdentity = NetworkIdentityFactory.fromSnapshot(first)
    val secondIdentity = NetworkIdentityFactory.fromSnapshot(second)

    assertNotNull(firstIdentity)
    assertEquals(firstIdentity?.networkKey, secondIdentity?.networkKey)
  }

  @Test
  fun gatewayChangeProducesDifferentNetworkKey() {
    val first = NetworkSnapshot(
      connected = true,
      isWifi = true,
      transportLabel = "Wi-Fi",
      subnet = "192.168.1.0/24",
      gateway = "192.168.1.1",
      dnsServers = listOf("192.168.1.1")
    )
    val second = first.copy(gateway = "192.168.50.1")

    val firstIdentity = NetworkIdentityFactory.fromSnapshot(first)
    val secondIdentity = NetworkIdentityFactory.fromSnapshot(second)

    assertNotNull(firstIdentity)
    assertNotNull(secondIdentity)
    assertNotEquals(firstIdentity?.networkKey, secondIdentity?.networkKey)
  }
}
