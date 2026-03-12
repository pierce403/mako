package ninja.mako.discovery

import java.io.File

object NeighborTableReader {
  private const val ARP_TABLE_PATH = "/proc/net/arp"

  fun macAddressForHost(host: String): String? {
    return runCatching {
      File(ARP_TABLE_PATH).useLines { lines ->
        lines
          .drop(1)
          .mapNotNull(::parseEntry)
          .firstOrNull { entry -> entry.ipAddress == host }
          ?.macAddress
      }
    }.getOrNull()
  }

  private fun parseEntry(line: String): NeighborEntry? {
    val columns = line.trim().split(Regex("\\s+"))
    if (columns.size < 4) return null

    val macAddress = normalizeMacAddress(columns[3]) ?: return null
    if (macAddress == UNKNOWN_MAC) return null

    return NeighborEntry(
      ipAddress = columns[0],
      macAddress = macAddress
    )
  }

  private fun normalizeMacAddress(value: String): String? {
    val cleaned = value.replace("-", ":").trim()
    if (!MAC_ADDRESS_REGEX.matches(cleaned)) return null
    return cleaned.uppercase()
  }

  private data class NeighborEntry(
    val ipAddress: String,
    val macAddress: String
  )

  private val MAC_ADDRESS_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
  private const val UNKNOWN_MAC = "00:00:00:00:00:00"
}
