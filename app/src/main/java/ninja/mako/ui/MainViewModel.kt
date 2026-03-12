package ninja.mako.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ninja.mako.data.NetworkRepository
import ninja.mako.discovery.HostCandidatePlanner
import ninja.mako.discovery.HostDiscoveryPlan
import ninja.mako.discovery.HostSweepSession
import ninja.mako.discovery.HostSweepStatus
import ninja.mako.discovery.TcpHostSweepRunner
import ninja.mako.network.NetworkIdentityFactory
import ninja.mako.network.NetworkMonitor
import ninja.mako.network.NetworkSnapshot

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
  private val networkMonitor = NetworkMonitor(application.applicationContext)
  private val repository = NetworkRepository(application.applicationContext)
  private val hostSweepRunner = TcpHostSweepRunner()
  private val currentNetworkKey = MutableStateFlow<String?>(null)
  private val sweepSession = MutableStateFlow<HostSweepSession?>(null)
  private val filterQuery = MutableStateFlow("")
  private val sortMode = MutableStateFlow(DeviceSortMode.RECENT)
  private val openServiceOnly = MutableStateFlow(false)
  private val infrastructureOnly = MutableStateFlow(false)
  private val webInterfacesOnly = MutableStateFlow(false)
  private var sweepJob: Job? = null
  private var activeSweepSignature: String? = null

  private val networkSnapshots = networkMonitor
    .snapshots()
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
      initialValue = NetworkSnapshot()
    )

  private val currentNetworkRecord = currentNetworkKey
    .flatMapLatest { key ->
      if (key == null) flowOf(null) else repository.observeRecord(key)
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
      initialValue = null
    )

  private val knownNetworkCount = repository
    .observeCount()
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
      initialValue = 0
    )

  private val discoveryPlan = networkSnapshots
    .map { snapshot -> HostCandidatePlanner.buildPlan(snapshot) }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
      initialValue = null
    )

  private val liveTicker = flow {
    while (true) {
      emit(System.currentTimeMillis())
      delay(LiveHostWindow.TICK_MS)
    }
  }
    .onStart { emit(System.currentTimeMillis()) }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
      initialValue = System.currentTimeMillis()
    )

  init {
    viewModelScope.launch {
      var previousNetworkKey: String? = null

      networkSnapshots.collect { snapshot ->
        val identity = NetworkIdentityFactory.fromSnapshot(snapshot)
        if (identity == null) {
          currentNetworkKey.value = null
          previousNetworkKey = null
          return@collect
        }

        val isActivation = previousNetworkKey != identity.networkKey
        repository.persistSnapshot(identity, snapshot, isActivation)
        currentNetworkKey.value = identity.networkKey
        previousNetworkKey = identity.networkKey
      }
    }

    viewModelScope.launch {
      combine(currentNetworkKey, discoveryPlan) { networkKey, plan ->
        networkKey to plan
      }.collect { (networkKey, plan) ->
        val stableNetworkKey = networkKey
        val stablePlan = plan
        val signature =
          if (stableNetworkKey == null || stablePlan == null) null
          else buildSweepSignature(stableNetworkKey, stablePlan)

        if (signature == null) {
          cancelSweep()
          sweepSession.value = null
          return@collect
        }

        if (signature == activeSweepSignature) return@collect

        cancelSweep()
        activeSweepSignature = signature
        sweepSession.value = HostSweepSession.running(stableNetworkKey!!, stablePlan!!, hostSweepRunner.ports())
        sweepJob = viewModelScope.launch {
          try {
            sweepSession.value = hostSweepRunner.run(stableNetworkKey, stablePlan) { progress ->
              sweepSession.value = progress
            }
          } catch (cancelled: kotlinx.coroutines.CancellationException) {
            sweepSession.value = sweepSession.value?.copy(
              status = HostSweepStatus.CANCELLED,
              endedAt = System.currentTimeMillis()
            )
            throw cancelled
          } catch (_: Exception) {
            sweepSession.value = sweepSession.value?.copy(
              status = HostSweepStatus.FAILED,
              endedAt = System.currentTimeMillis()
            )
          }
        }
      }
    }
  }

  private val detectedDevices = combine(networkSnapshots, currentNetworkKey, sweepSession) { snapshot, networkKey, session ->
    withContext(Dispatchers.Default) {
      DetectedDeviceInventoryBuilder.build(snapshot, networkKey, session)
    }
  }
    .conflate()
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
      initialValue = emptyList()
    )

  val devices: StateFlow<List<DiscoveredDeviceListItem>> = combine(
    detectedDevices,
    filterQuery,
    openServiceOnly,
    infrastructureOnly,
    webInterfacesOnly
  ) { devices, query, openServices, infrastructure, webInterfaces ->
    devices.filter { device ->
      val matchesQuery =
        query.isBlank() ||
          device.searchText.contains(query, ignoreCase = true) ||
          device.hostAddress.contains(query, ignoreCase = true)
      val matchesOpenService = !openServices || device.isOpenService
      val matchesInfrastructure = !infrastructure || device.isInfrastructure
      val matchesWebInterface = !webInterfaces || device.isWebInterface
      matchesQuery && matchesOpenService && matchesInfrastructure && matchesWebInterface
    }
  }
    .combine(sortMode) { devices, mode ->
      when (mode) {
        DeviceSortMode.RECENT -> devices.sortedWith(
          compareByDescending<DiscoveredDeviceListItem> { it.isLocalDevice }
            .thenByDescending { it.sortTimestamp }
        )
        DeviceSortMode.ADDRESS -> devices.sortedWith(
          compareByDescending<DiscoveredDeviceListItem> { it.isLocalDevice }
            .thenBy { ipv4SortKey(it.hostAddress) }
            .thenBy { it.hostAddress }
        )
        DeviceSortMode.IDENTITY -> devices.sortedWith(
          compareByDescending<DiscoveredDeviceListItem> { it.isLocalDevice }
            .thenBy { it.displayTitle.lowercase() }
            .thenBy { it.hostAddress }
        )
      }
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
      initialValue = emptyList()
    )

  val filterState: StateFlow<DeviceFilterState> = combine(
    filterQuery,
    sortMode,
    openServiceOnly,
    infrastructureOnly,
    webInterfacesOnly
  ) { query, sort, openServices, infrastructure, webInterfaces ->
    DeviceFilterState(
      query = query,
      sortMode = sort,
      openServiceOnly = openServices,
      infrastructureOnly = infrastructure,
      webInterfacesOnly = webInterfaces
    )
  }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
      initialValue = DeviceFilterState()
    )

  val deviceListSummary: StateFlow<String> = combine(detectedDevices, devices, liveTicker) { rawDevices, visibleDevices, now ->
    when {
      rawDevices.isEmpty() -> "No local Wi-Fi devices in inventory yet."
      visibleDevices.isEmpty() -> "No hosts match the current filters."
      visibleDevices.size == rawDevices.size -> {
        val liveCount = rawDevices.count { device ->
          device.isLocalDevice || LiveHostWindow.isLive(device.lastSeen, now)
        }
        "${rawDevices.size} ${deviceLabel(rawDevices.size)} in local inventory • $liveCount active now"
      }
      else -> "Showing ${visibleDevices.size} of ${rawDevices.size} ${deviceLabel(rawDevices.size)}"
    }
  }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
      initialValue = "No local Wi-Fi devices in inventory yet."
    )

  val uiState: StateFlow<MainUiState> = combine(
    networkSnapshots,
    currentNetworkRecord,
    knownNetworkCount,
    discoveryPlan,
    sweepSession
  ) { snapshot, record, count, activeDiscoveryPlan, activeSweepSession ->
    MainUiState.from(snapshot, record, count, activeDiscoveryPlan, activeSweepSession)
  }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
      initialValue = MainUiState()
    )

  fun updateQuery(query: String) {
    filterQuery.value = query
  }

  fun updateSortMode(mode: DeviceSortMode) {
    sortMode.value = mode
  }

  fun setOpenServiceOnly(enabled: Boolean) {
    openServiceOnly.value = enabled
  }

  fun setInfrastructureOnly(enabled: Boolean) {
    infrastructureOnly.value = enabled
  }

  fun setWebInterfacesOnly(enabled: Boolean) {
    webInterfacesOnly.value = enabled
  }

  private suspend fun cancelSweep() {
    sweepJob?.cancelAndJoin()
    sweepJob = null
    activeSweepSignature = null
  }

  private fun buildSweepSignature(networkKey: String, plan: HostDiscoveryPlan): String {
    return buildString {
      append(networkKey)
      append("|")
      append(plan.subnetCidr)
      append("|")
      append(plan.candidateHosts.size)
      append("|")
      append(plan.candidateHosts.take(8).joinToString(","))
    }
  }

  private fun ipv4SortKey(address: String): Long {
    val parts = address.split(".")
    if (parts.size != 4) return Long.MAX_VALUE

    var value = 0L
    for (part in parts) {
      val octet = part.toIntOrNull() ?: return Long.MAX_VALUE
      if (octet !in 0..255) return Long.MAX_VALUE
      value = (value shl 8) or octet.toLong()
    }
    return value
  }

  private fun deviceLabel(count: Int): String {
    return if (count == 1) "device" else "devices"
  }

  private object LiveHostWindow {
    const val WINDOW_MS = 120_000L
    const val TICK_MS = 5_000L

    fun isLive(lastSeen: Long, now: Long): Boolean {
      return now - lastSeen <= WINDOW_MS
    }
  }
}
