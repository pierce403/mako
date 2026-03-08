package ninja.mako.discovery

import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.coroutineContext

class TcpHostSweepRunner(
  private val ports: List<Int> = listOf(53, 80, 443, 445, 631),
  private val maxConcurrentHosts: Int = 12,
  private val connectTimeoutMs: Int = 250
) {
  suspend fun run(networkKey: String, plan: HostDiscoveryPlan): HostSweepSession = coroutineScope {
    val semaphore = Semaphore(maxConcurrentHosts)
    val startedAt = System.currentTimeMillis()

    val results = plan.candidateHosts.map { host ->
      async(Dispatchers.IO) {
        semaphore.withPermit {
          probeHost(host)
        }
      }
    }.awaitAll()

    val reachable = results.filter { result ->
      result.outcome == HostProbeOutcome.CONNECTED || result.outcome == HostProbeOutcome.REFUSED
    }

    HostSweepSession(
      networkKey = networkKey,
      subnetCidr = plan.subnetCidr,
      status = HostSweepStatus.COMPLETED,
      startedAt = startedAt,
      endedAt = System.currentTimeMillis(),
      hostsPlanned = plan.candidateHosts.size,
      hostsAttempted = results.size,
      reachableHosts = reachable.size,
      openServiceHosts = results.count { result -> result.outcome == HostProbeOutcome.CONNECTED },
      portsProbed = ports,
      sampleReachableHosts = reachable.take(6).map { result ->
        if (result.port == null) result.host else "${result.host}:${result.port}"
      },
      results = results
    )
  }

  fun ports(): List<Int> = ports

  private suspend fun probeHost(host: String): HostProbeResult {
    var lastFailure = HostProbeOutcome.UNREACHABLE

    for (port in ports) {
      coroutineContext.ensureActive()

      val result = tryConnect(host, port)
      when (result.outcome) {
        HostProbeOutcome.CONNECTED,
        HostProbeOutcome.REFUSED -> return result
        HostProbeOutcome.TIMEOUT,
        HostProbeOutcome.UNREACHABLE,
        HostProbeOutcome.FAILED -> lastFailure = result.outcome
      }
    }

    return HostProbeResult(host = host, outcome = lastFailure)
  }

  private fun tryConnect(host: String, port: Int): HostProbeResult {
    return try {
      Socket().use { socket ->
        socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
      }
      HostProbeResult(host = host, outcome = HostProbeOutcome.CONNECTED, port = port)
    } catch (_: ConnectException) {
      HostProbeResult(host = host, outcome = HostProbeOutcome.REFUSED, port = port)
    } catch (_: SocketTimeoutException) {
      HostProbeResult(host = host, outcome = HostProbeOutcome.TIMEOUT, port = port)
    } catch (_: NoRouteToHostException) {
      HostProbeResult(host = host, outcome = HostProbeOutcome.UNREACHABLE, port = port)
    } catch (_: UnknownHostException) {
      HostProbeResult(host = host, outcome = HostProbeOutcome.FAILED, port = port)
    } catch (_: SecurityException) {
      HostProbeResult(host = host, outcome = HostProbeOutcome.FAILED, port = port)
    } catch (_: Exception) {
      HostProbeResult(host = host, outcome = HostProbeOutcome.FAILED, port = port)
    }
  }
}
