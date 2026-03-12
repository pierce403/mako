package ninja.mako.discovery

import ninja.mako.network.NetworkSnapshot

object DeviceClassifier {
  fun classify(
    snapshot: NetworkSnapshot,
    result: HostProbeResult,
    hostname: String?,
    manufacturer: String?
  ): DeviceClassification {
    val hostNameText = hostname.orEmpty().lowercase()
    val vendorText = manufacturer.orEmpty().lowercase()

    if (result.host == snapshot.gateway) {
      return classification(
        label = "Router / access point",
        badgeLabel = "Gateway",
        confidence = ClassificationConfidence.HIGH,
        evidence = listOf("Host matches the active network gateway.")
      )
    }

    if (snapshot.dnsServers.contains(result.host)) {
      return classification(
        label = "Resolver / infrastructure",
        badgeLabel = "Resolver",
        confidence = if (result.port == 53) ClassificationConfidence.HIGH else ClassificationConfidence.MEDIUM,
        evidence = buildList {
          add("Host is one of the configured DNS resolvers.")
          if (result.port == 53) add("TCP 53 responded during the sweep.")
        }
      )
    }

    if (
      result.port == 631 ||
      containsAny(hostNameText, "printer", "ipp", "laserjet", "deskjet", "officejet") ||
      (containsAny(vendorText, "brother", "epson", "canon", "xerox", "ricoh", "lexmark", "kyocera") &&
        containsAny(hostNameText, "print", "printer", "ipp"))
    ) {
      return classification(
        label = "Printer",
        badgeLabel = "Printer",
        confidence = if (result.port == 631) ClassificationConfidence.HIGH else ClassificationConfidence.MEDIUM,
        evidence = buildList {
          if (result.port == 631) add("IPP printer port 631 responded.")
          if (containsAny(hostNameText, "printer", "ipp", "laserjet", "deskjet", "officejet")) {
            add("Hostname looks printer-related.")
          }
          if (manufacturer != null) add("Vendor hint: $manufacturer.")
        }
      )
    }

    if (
      result.port == 445 ||
      containsAny(hostNameText, "nas", "diskstation", "truenas", "qnap", "synology", "freenas") ||
      containsAny(vendorText, "synology", "qnap", "western digital", "wd", "netapp", "truenas")
    ) {
      return classification(
        label = "NAS / file server",
        badgeLabel = "NAS",
        confidence = when {
          containsAny(hostNameText, "nas", "diskstation", "truenas", "qnap", "synology", "freenas") -> ClassificationConfidence.HIGH
          result.port == 445 -> ClassificationConfidence.MEDIUM
          else -> ClassificationConfidence.MEDIUM
        },
        evidence = buildList {
          if (result.port == 445) add("SMB port 445 responded.")
          if (containsAny(hostNameText, "nas", "diskstation", "truenas", "qnap", "synology", "freenas")) {
            add("Hostname looks NAS-related.")
          }
          if (manufacturer != null) add("Vendor hint: $manufacturer.")
        }
      )
    }

    if (
      containsAny(hostNameText, "camera", "cam", "nvr", "doorbell") ||
      containsAny(vendorText, "hikvision", "dahua", "axis", "reolink", "arlo", "ring", "amcrest", "ezviz", "wyze")
    ) {
      return classification(
        label = "Camera",
        badgeLabel = "Camera",
        confidence = if (containsAny(hostNameText, "camera", "cam", "nvr", "doorbell")) {
          ClassificationConfidence.HIGH
        } else {
          ClassificationConfidence.MEDIUM
        },
        evidence = buildList {
          if (containsAny(hostNameText, "camera", "cam", "nvr", "doorbell")) {
            add("Hostname looks camera-related.")
          }
          if (manufacturer != null) add("Vendor hint: $manufacturer.")
          if (result.port == 80 || result.port == 443) add("Web-managed endpoint responded.")
        }
      )
    }

    if (
      containsAny(hostNameText, "hue", "bulb", "light", "lamp", "switch", "plug", "shelly", "lifx", "kasa", "tuya") ||
      containsAny(vendorText, "signify", "lifx", "tuya", "shelly", "meross", "yeelight")
    ) {
      return classification(
        label = "Light / smart-home device",
        badgeLabel = "Light",
        confidence = if (containsAny(hostNameText, "bulb", "light", "lamp")) {
          ClassificationConfidence.HIGH
        } else {
          ClassificationConfidence.MEDIUM
        },
        evidence = buildList {
          if (containsAny(hostNameText, "hue", "bulb", "light", "lamp", "switch", "plug", "shelly", "lifx", "kasa", "tuya")) {
            add("Hostname looks smart-home related.")
          }
          if (manufacturer != null) add("Vendor hint: $manufacturer.")
        }
      )
    }

    if (
      containsAny(hostNameText, "desktop-", "laptop", "macbook", "imac", "workstation", "thinkpad", "surface", "minipc") ||
      (result.port == 445 && containsAny(vendorText, "apple", "dell", "lenovo", "hewlett packard", "hp inc", "microsoft", "acer", "msi"))
    ) {
      return classification(
        label = "Desktop / laptop",
        badgeLabel = "Desktop",
        confidence = if (containsAny(hostNameText, "desktop-", "laptop", "macbook", "imac", "workstation", "thinkpad", "surface", "minipc")) {
          ClassificationConfidence.HIGH
        } else {
          ClassificationConfidence.MEDIUM
        },
        evidence = buildList {
          if (containsAny(hostNameText, "desktop-", "laptop", "macbook", "imac", "workstation", "thinkpad", "surface", "minipc")) {
            add("Hostname looks desktop or laptop related.")
          }
          if (result.port == 445) add("SMB port 445 responded.")
          if (manufacturer != null) add("Vendor hint: $manufacturer.")
        }
      )
    }

    if (
      containsAny(hostNameText, "iphone", "ipad", "pixel", "galaxy", "android", "phone", "tablet") ||
      containsAny(vendorText, "google", "oneplus", "xiaomi")
    ) {
      return classification(
        label = "Phone / tablet",
        badgeLabel = "Phone",
        confidence = if (containsAny(hostNameText, "iphone", "ipad", "pixel", "galaxy", "android", "phone", "tablet")) {
          ClassificationConfidence.HIGH
        } else {
          ClassificationConfidence.LOW
        },
        evidence = buildList {
          if (containsAny(hostNameText, "iphone", "ipad", "pixel", "galaxy", "android", "phone", "tablet")) {
            add("Hostname looks mobile-device related.")
          }
          if (manufacturer != null) add("Vendor hint: $manufacturer.")
        }
      )
    }

    if (result.port == 80 || result.port == 443) {
      return classification(
        label = "Web-managed device",
        badgeLabel = "Web",
        confidence = ClassificationConfidence.LOW,
        evidence = listOf("HTTP or HTTPS responded during the sweep.")
      )
    }

    if (result.port == 53) {
      return classification(
        label = "DNS service",
        badgeLabel = "DNS",
        confidence = ClassificationConfidence.LOW,
        evidence = listOf("TCP 53 responded during the sweep.")
      )
    }

    return DeviceClassification.unknown(
      evidence = listOf("No stronger hostname, vendor, or service-based classification matched.")
    )
  }

  private fun classification(
    label: String,
    badgeLabel: String,
    confidence: ClassificationConfidence,
    evidence: List<String>
  ) = DeviceClassification(
    label = label,
    badgeLabel = badgeLabel,
    confidence = confidence,
    evidence = evidence
  )

  private fun containsAny(value: String, vararg needles: String): Boolean {
    if (value.isBlank()) return false
    return needles.any { needle -> value.contains(needle) }
  }
}
