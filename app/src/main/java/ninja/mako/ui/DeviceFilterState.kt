package ninja.mako.ui

data class DeviceFilterState(
  val query: String = "",
  val sortMode: DeviceSortMode = DeviceSortMode.RECENT,
  val openServiceOnly: Boolean = false,
  val infrastructureOnly: Boolean = false,
  val webInterfacesOnly: Boolean = false
)
