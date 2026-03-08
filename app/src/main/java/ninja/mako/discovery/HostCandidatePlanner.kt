package ninja.mako.discovery

import java.net.Inet4Address
import java.net.InetAddress
import kotlin.math.max
import kotlin.math.min
import ninja.mako.network.NetworkSnapshot

object HostCandidatePlanner {
  private const val DEFAULT_MAX_HOSTS = 64

  fun buildPlan(snapshot: NetworkSnapshot, maxHosts: Int = DEFAULT_MAX_HOSTS): HostDiscoveryPlan? {
    if (!snapshot.connected || !snapshot.isWifi) return null

    val subnet = parseIpv4Cidr(snapshot.subnet ?: return null) ?: return null
    val usableHosts = subnet.usableHosts()
    if (usableHosts.isEmpty()) return null

    val localAddress = snapshot.localAddress?.let(::parseIpv4Address)
    val gatewayAddress = snapshot.gateway?.let(::parseIpv4Address)
    val dnsAddresses = snapshot.dnsServers.mapNotNull(::parseIpv4Address)

    val prioritized = linkedSetOf<Int>()
    gatewayAddress?.takeIf(subnet::contains)?.let(prioritized::add)
    dnsAddresses.filter(subnet::contains).forEach(prioritized::add)
    localAddress?.let { local ->
      if (subnet.contains(local)) {
        val radius = min(16, usableHosts.count())
        for (offset in 1..radius) {
          (local - offset).takeIf(subnet::contains)?.let(prioritized::add)
          (local + offset).takeIf(subnet::contains)?.let(prioritized::add)
        }
      }
    }

    val finalHosts = linkedSetOf<Int>()
    prioritized
      .filterNot { address -> address == localAddress }
      .take(maxHosts)
      .forEach(finalHosts::add)

    if (finalHosts.size < maxHosts) {
      usableHosts
        .asSequence()
        .filterNot { address -> address == localAddress }
        .filterNot(finalHosts::contains)
        .take(maxHosts - finalHosts.size)
        .forEach(finalHosts::add)
    }

    if (finalHosts.isEmpty()) return null

    return HostDiscoveryPlan(
      subnetCidr = snapshot.subnet,
      candidateHosts = finalHosts.map(::formatIpv4Address),
      prioritizedHosts = prioritized.map(::formatIpv4Address),
      totalUsableHostCount = usableHosts.count { it != localAddress },
      truncated = finalHosts.size < max(0, usableHosts.count { it != localAddress })
    )
  }

  private fun parseIpv4Cidr(value: String): Ipv4Subnet? {
    val parts = value.split("/")
    if (parts.size != 2) return null
    val address = parseIpv4Address(parts[0]) ?: return null
    val prefixLength = parts[1].toIntOrNull() ?: return null
    if (prefixLength !in 0..32) return null

    val mask = if (prefixLength == 0) 0 else (0xFFFFFFFF.toInt() shl (32 - prefixLength))
    val network = address and mask
    return Ipv4Subnet(network, prefixLength)
  }

  private fun parseIpv4Address(value: String): Int? {
    return runCatching {
      val bytes = InetAddress.getByName(value).address
      if (bytes.size != 4 || InetAddress.getByAddress(bytes) !is Inet4Address) return null
      bytes.fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
    }.getOrNull()
  }

  private fun formatIpv4Address(value: Int): String {
    return listOf(
      (value ushr 24) and 0xFF,
      (value ushr 16) and 0xFF,
      (value ushr 8) and 0xFF,
      value and 0xFF
    ).joinToString(".")
  }

  private data class Ipv4Subnet(
    val networkAddress: Int,
    val prefixLength: Int
  ) {
    fun contains(address: Int): Boolean {
      if (prefixLength == 0) return true
      val mask = 0xFFFFFFFF.toInt() shl (32 - prefixLength)
      return (address and mask) == networkAddress
    }

    fun usableHosts(): IntRange {
      return when {
        prefixLength >= 31 -> IntRange.EMPTY
        else -> {
          val hostBits = 32 - prefixLength
          val broadcast = networkAddress or ((1 shl hostBits) - 1)
          (networkAddress + 1)..(broadcast - 1)
        }
      }
    }
  }
}
