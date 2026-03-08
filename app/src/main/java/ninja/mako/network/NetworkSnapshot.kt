package ninja.mako.network

data class NetworkSnapshot(
  val connected: Boolean = false,
  val isWifi: Boolean = false,
  val transportLabel: String = "Unavailable",
  val interfaceName: String? = null,
  val localAddress: String? = null,
  val subnet: String? = null,
  val gateway: String? = null,
  val dnsServers: List<String> = emptyList(),
  val domains: String? = null,
  val privateDnsServerName: String? = null,
  val isMetered: Boolean = false,
  val isValidated: Boolean = false,
  val hasCaptivePortal: Boolean = false,
  val hasVpnTransport: Boolean = false
)
