package ninja.mako.ui

import java.io.Serializable

data class DiscoveredDeviceDetail(
  val title: String,
  val hostAddress: String,
  val badgeLabel: String,
  val metaLine: String,
  val statusLine: String,
  val report: String
) : Serializable
