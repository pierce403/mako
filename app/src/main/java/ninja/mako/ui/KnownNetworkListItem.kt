package ninja.mako.ui

data class KnownNetworkListItem(
  val networkKey: String,
  val title: String,
  val subtitle: String,
  val badgeLabel: String,
  val isSelected: Boolean,
  val isLive: Boolean
)
