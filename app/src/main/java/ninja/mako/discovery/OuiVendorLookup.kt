package ninja.mako.discovery

import android.content.Context

object OuiVendorLookup {
  private const val ASSET_NAME = "ieee_oui.csv"

  @Volatile
  private var cachedVendors: Map<String, String>? = null

  fun lookup(context: Context, macAddress: String): String? {
    val assignment = assignmentFromMac(macAddress) ?: return null
    val vendors = cachedVendors ?: synchronized(this) {
      cachedVendors ?: loadVendors(context).also { cachedVendors = it }
    }
    return vendors[assignment]
  }

  private fun assignmentFromMac(macAddress: String): String? {
    val hex = macAddress
      .filter { it.isLetterOrDigit() }
      .uppercase()
    if (hex.length < 6) return null
    return hex.take(6)
  }

  private fun loadVendors(context: Context): Map<String, String> {
    return runCatching {
      buildMap {
        context.assets.open(ASSET_NAME).bufferedReader().useLines { lines ->
          lines.drop(1).forEach { line ->
            val columns = parseCsvLine(line)
            if (columns.size < 3 || columns[0] != "MA-L") return@forEach
            val assignment = columns[1].trim().uppercase()
            val vendor = columns[2].trim()
            if (assignment.length == 6 && vendor.isNotBlank()) {
              putIfAbsent(assignment, vendor)
            }
          }
        }
      }
    }.getOrElse { emptyMap() }
  }

  private fun parseCsvLine(line: String): List<String> {
    val columns = mutableListOf<String>()
    val current = StringBuilder()
    var insideQuotes = false

    line.forEach { char ->
      when {
        char == '"' -> insideQuotes = !insideQuotes
        char == ',' && !insideQuotes -> {
          columns += current.toString()
          current.setLength(0)
        }
        else -> current.append(char)
      }
    }

    columns += current.toString()
    return columns
  }
}
