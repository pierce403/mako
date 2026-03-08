package ninja.mako.discovery

data class HostSweepSession(
  val networkKey: String,
  val subnetCidr: String,
  val status: HostSweepStatus,
  val startedAt: Long,
  val endedAt: Long? = null,
  val hostsPlanned: Int,
  val hostsAttempted: Int,
  val reachableHosts: Int,
  val openServiceHosts: Int,
  val portsProbed: List<Int>,
  val sampleReachableHosts: List<String>,
  val results: List<HostProbeResult>
) {
  companion object {
    fun running(
      networkKey: String,
      plan: HostDiscoveryPlan,
      ports: List<Int>
    ): HostSweepSession {
      return HostSweepSession(
        networkKey = networkKey,
        subnetCidr = plan.subnetCidr,
        status = HostSweepStatus.RUNNING,
        startedAt = System.currentTimeMillis(),
        hostsPlanned = plan.candidateHosts.size,
        hostsAttempted = 0,
        reachableHosts = 0,
        openServiceHosts = 0,
        portsProbed = ports,
        sampleReachableHosts = emptyList(),
        results = emptyList()
      )
    }
  }
}

enum class HostSweepStatus {
  RUNNING,
  COMPLETED,
  CANCELLED,
  FAILED
}
