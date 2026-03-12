package ninja.mako.ui

data class DiscoveredDeviceListItem(
  val deviceKey: String,
  val displayTitle: String,
  val hostAddress: String,
  val badgeLabel: String,
  val metaLine: String,
  val statusLine: String,
  val detail: DiscoveredDeviceDetail,
  val searchText: String,
  val sortTimestamp: Long,
  val lastSeen: Long,
  val isOpenService: Boolean,
  val isInfrastructure: Boolean,
  val isWebInterface: Boolean,
  val isLocalDevice: Boolean
)
