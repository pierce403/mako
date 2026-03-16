package ninja.mako.discovery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object HttpBannerGrabber {
  private val permissiveContext by lazy {
    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
      override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
      override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
      override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
    })
    val sc = SSLContext.getInstance("SSL")
    sc.init(null, trustAllCerts, SecureRandom())
    sc
  }

  suspend fun grab(host: String, port: Int, useHttps: Boolean = port == 443 || port == 8443): HttpBanner? = withContext(Dispatchers.IO) {
    val protocol = if (useHttps) "https" else "http"
    val timeoutMs = 2000

    return@withContext runCatching {
      val url = URL("$protocol://$host:$port/")
      val connection = url.openConnection() as HttpURLConnection
      connection.connectTimeout = timeoutMs
      connection.readTimeout = timeoutMs
      connection.requestMethod = "GET"
      
      if (connection is HttpsURLConnection) {
        connection.sslSocketFactory = permissiveContext.socketFactory
        connection.setHostnameVerifier { _, _ -> true }
      }

      val serverHeader = connection.getHeaderField("Server")
      
      val title = runCatching {
         val contentType = connection.getHeaderField("Content-Type") ?: ""
         if (contentType.contains("text/html", ignoreCase = true) || contentType.isEmpty()) {
           val scanner = java.util.Scanner(connection.inputStream, "UTF-8").useDelimiter("\\A")
           val content = if (scanner.hasNext()) scanner.next().take(16384) else ""
           val titleMatch = Regex("<title>([^<]*)</title>", RegexOption.IGNORE_CASE).find(content)
           titleMatch?.groupValues?.get(1)?.trim()
         } else {
           null
         }
      }.getOrNull()

      HttpBanner(
        server = serverHeader?.ifBlank { null },
        title = title?.ifBlank { null }
      )
    }.getOrNull()
  }
}

data class HttpBanner(
  val server: String?,
  val title: String?
)
