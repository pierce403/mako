package ninja.mako.discovery

enum class ClassificationConfidence {
  HIGH,
  MEDIUM,
  LOW;

  fun label(): String = name.lowercase().replaceFirstChar { it.uppercase() }
}

data class DeviceClassification(
  val label: String,
  val badgeLabel: String,
  val confidence: ClassificationConfidence,
  val evidence: List<String>
) {
  companion object {
    fun unknown(evidence: List<String> = emptyList()): DeviceClassification {
      return DeviceClassification(
        label = "Observed host",
        badgeLabel = "Host",
        confidence = ClassificationConfidence.LOW,
        evidence = evidence
      )
    }
  }
}
