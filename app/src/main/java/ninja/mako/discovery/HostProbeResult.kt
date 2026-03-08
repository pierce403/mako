package ninja.mako.discovery

data class HostProbeResult(
  val host: String,
  val outcome: HostProbeOutcome,
  val port: Int? = null
)

enum class HostProbeOutcome {
  CONNECTED,
  REFUSED,
  TIMEOUT,
  UNREACHABLE,
  FAILED
}
