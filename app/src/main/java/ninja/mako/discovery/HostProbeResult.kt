package ninja.mako.discovery

data class HostProbeResult(
  val host: String,
  val outcome: HostProbeOutcome,
  val port: Int? = null,
  val observedAt: Long = System.currentTimeMillis()
)

enum class HostProbeOutcome {
  CONNECTED,
  REFUSED,
  TIMEOUT,
  UNREACHABLE,
  FAILED
}
