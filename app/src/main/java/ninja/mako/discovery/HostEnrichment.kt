package ninja.mako.discovery

data class HostEnrichment(
  val hostname: String? = null,
  val macAddress: String? = null,
  val manufacturer: String? = null,
  val httpServer: String? = null,
  val httpTitle: String? = null,
  val classification: DeviceClassification = DeviceClassification.unknown(),
  val evidenceSources: List<String> = emptyList(),
  val observedAt: Long = System.currentTimeMillis()
)
