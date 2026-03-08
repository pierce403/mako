package ninja.mako.discovery

data class HostDiscoveryPlan(
  val subnetCidr: String,
  val candidateHosts: List<String>,
  val prioritizedHosts: List<String>,
  val totalUsableHostCount: Int,
  val truncated: Boolean
)
