package ninja.mako.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ninja.mako.network.NetworkSnapshot

class DeviceClassifierTest {
  @Test
  fun classifiesGatewayAsInfrastructure() {
    val snapshot = NetworkSnapshot(
      connected = true,
      isWifi = true,
      transportLabel = "Wi-Fi",
      gateway = "192.168.1.1"
    )

    val classification = DeviceClassifier.classify(
      snapshot = snapshot,
      result = HostProbeResult(
        host = "192.168.1.1",
        outcome = HostProbeOutcome.CONNECTED,
        port = 443
      ),
      hostname = "gateway.local",
      manufacturer = "Ubiquiti"
    )

    assertEquals("Router / access point", classification.label)
    assertEquals("Gateway", classification.badgeLabel)
    assertEquals(ClassificationConfidence.HIGH, classification.confidence)
  }

  @Test
  fun classifiesNasFromHostnameAndSmb() {
    val classification = DeviceClassifier.classify(
      snapshot = NetworkSnapshot(),
      result = HostProbeResult(
        host = "192.168.1.77",
        outcome = HostProbeOutcome.REFUSED,
        port = 445
      ),
      hostname = "diskstation.office",
      manufacturer = "Synology"
    )

    assertEquals("NAS / file server", classification.label)
    assertEquals("NAS", classification.badgeLabel)
    assertEquals(ClassificationConfidence.HIGH, classification.confidence)
    assertTrue(classification.evidence.any { it.contains("Hostname looks NAS-related") })
  }

  @Test
  fun fallsBackToObservedHostWhenNoEvidenceMatches() {
    val classification = DeviceClassifier.classify(
      snapshot = NetworkSnapshot(),
      result = HostProbeResult(
        host = "192.168.1.55",
        outcome = HostProbeOutcome.CONNECTED,
        port = 1234
      ),
      hostname = null,
      manufacturer = null
    )

    assertEquals("Observed host", classification.label)
    assertEquals("Host", classification.badgeLabel)
    assertEquals(ClassificationConfidence.LOW, classification.confidence)
  }
}
