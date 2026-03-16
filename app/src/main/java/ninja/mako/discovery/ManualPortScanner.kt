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

  suspend fun scanPorts(host: String): List<PortScanResult> = coroutineScope {
    val semaphore = Semaphore(10)
    
    val openPorts = EXTENDED_PORTS.map { port ->
      async(Dispatchers.IO) {
        semaphore.withPermit {
          val result = tryConnectAndGrabBanner(host, port)
          if (result != null) {
            if (result.banner == null || result.banner.isEmpty()) {
              val useHttps = port == 443 || port == 8443
              val bannerRes = HttpBannerGrabber.grab(host, port, useHttps)
              if (bannerRes != null && (bannerRes.title != null || bannerRes.server != null)) {
                result.copy(
                  isHttp = !useHttps,
                  isHttps = useHttps,
                  banner = bannerRes.title ?: bannerRes.server
                )
              } else {
                val altUseHttps = !useHttps
                val altBannerRes = HttpBannerGrabber.grab(host, port, altUseHttps)
                if (altBannerRes != null && (altBannerRes.title != null || altBannerRes.server != null)) {
                  result.copy(
                    isHttp = !altUseHttps,
                    isHttps = altUseHttps,
                    banner = altBannerRes.title ?: altBannerRes.server
                  )
                } else {
                  val knownService = when(port) {
                    53 -> "DNS"
                    135 -> "RPC"
                    139 -> "NetBIOS"
                    445 -> "SMB"
                    548 -> "AFP"
                    631 -> "IPP"
                    3306 -> "MySQL"
                    3389 -> "RDP"
                    5353 -> "mDNS"
                    9100 -> "JetDirect"
                    else -> null
                  }
                  result.copy(banner = knownService)
                }
              }
            } else {
              result
            }
          } else {
            null
          }
        }
      }
    }.awaitAll().filterNotNull()
    
    openPorts.sortedBy { it.port }
  }

  private fun tryConnectAndGrabBanner(host: String, port: Int): PortScanResult? {
    return try {
      var rawBanner: String? = null
      Socket().use { socket ->
        socket.connect(InetSocketAddress(host, port), TIMEOUT_MS)
        socket.soTimeout = 250 // short wait for pre-auth banner
        
        try {
          val buffer = ByteArray(1024)
          val bytesRead = socket.getInputStream().read(buffer)
          if (bytesRead > 0) {
            val decoded = String(buffer, 0, bytesRead, Charsets.UTF_8).trim()
            if (decoded.isNotBlank()) {
              rawBanner = decoded.take(120).replace("\r", "").replace("\n", " ")
            }
          }
        } catch (_: Exception) {
          // Timeout, no spontaneous TCP banner
        }
      }
      PortScanResult(port = port, banner = rawBanner)
    } catch (_: ConnectException) {
      null
    } catch (_: SocketTimeoutException) {
      null
    } catch (_: Exception) {
      null
    }
  }
}

data class PortScanResult(
  val port: Int,
  val isHttp: Boolean = false,
  val isHttps: Boolean = false,
  val banner: String? = null
)
