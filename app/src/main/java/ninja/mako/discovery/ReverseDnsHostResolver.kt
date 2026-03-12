package ninja.mako.discovery

import java.net.InetAddress

object ReverseDnsHostResolver {
  fun lookup(host: String): String? {
    val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return null
    val resolved = runCatching { address.canonicalHostName }.getOrNull()?.trim().orEmpty()
    if (resolved.isBlank()) return null
    if (resolved == host) return null
    return resolved.trimEnd('.')
  }
}
