package ninja.mako.network

import java.security.MessageDigest

data class NetworkIdentity(
  val networkKey: String,
  val keyMaterial: String,
  val stableInputSummary: String
)

object NetworkIdentityFactory {
  fun fromSnapshot(snapshot: NetworkSnapshot): NetworkIdentity? {
    if (!snapshot.connected || !snapshot.isWifi) return null

    val stableInputs = linkedMapOf<String, String>()
    stableInputs["transport"] = snapshot.transportLabel
    snapshot.gateway?.takeIf { it.isNotBlank() }?.let { stableInputs["gateway"] = it }
    snapshot.subnet?.takeIf { it.isNotBlank() }?.let { stableInputs["subnet"] = it }
    snapshot.dnsServers
      .filter { it.isNotBlank() }
      .sorted()
      .takeIf { it.isNotEmpty() }
      ?.let { stableInputs["dns"] = it.joinToString(",") }
    snapshot.domains?.takeIf { it.isNotBlank() }?.let { stableInputs["domains"] = it }
    snapshot.privateDnsServerName?.takeIf { it.isNotBlank() }?.let { stableInputs["privateDns"] = it }

    if (stableInputs.size == 1 && stableInputs.containsKey("transport")) {
      return null
    }

    val keyMaterial = stableInputs.entries.joinToString("|") { (name, value) ->
      "$name=$value"
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(keyMaterial.toByteArray())
    val fingerprint = digest.joinToString("") { byte ->
      "%02x".format(byte.toInt() and 0xFF)
    }.take(16)

    return NetworkIdentity(
      networkKey = "wifi:$fingerprint",
      keyMaterial = keyMaterial,
      stableInputSummary = stableInputs.entries.joinToString(" | ") { (name, value) ->
        "$name:$value"
      }
    )
  }
}
