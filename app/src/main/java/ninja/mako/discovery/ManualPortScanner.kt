package ninja.mako.discovery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

object ManualPortScanner {
  // Common ports to scan manually when the user requests it
  val EXTENDED_PORTS = listOf(
    21, 22, 23, 25, 53, 80, 110, 111, 135, 139, 143, 443, 445, 548, 631, 
    993, 995, 1900, 3306, 3389, 5000, 5353, 8000, 8080, 8443, 9000, 9100
  )
  
  private const val TIMEOUT_MS = 300

  suspend fun scanPorts(host: String): List<Int> = coroutineScope {
    val semaphore = Semaphore(10)
    
    val openPorts = EXTENDED_PORTS.map { port ->
      async(Dispatchers.IO) {
        semaphore.withPermit {
          tryConnect(host, port)
        }
      }
    }.awaitAll().filterNotNull()
    
    openPorts.sorted()
  }

  private fun tryConnect(host: String, port: Int): Int? {
    return try {
      Socket().use { socket ->
        socket.connect(InetSocketAddress(host, port), TIMEOUT_MS)
      }
      port
    } catch (_: ConnectException) {
      // Refused is often considered closed, but it means the host is there.
      // Usually full port scanners list refused as closed.
      null
    } catch (_: SocketTimeoutException) {
      null
    } catch (_: Exception) {
      null
    }
  }
}
