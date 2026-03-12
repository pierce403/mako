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
import ninja.mako.discovery.HostEnrichment
import ninja.mako.discovery.HostEnrichmentResolver
import ninja.mako.discovery.HostProbeOutcome
import ninja.mako.discovery.HostProbeResult
import ninja.mako.discovery.ScanPreferences
import ninja.mako.discovery.HostSweepSession
import ninja.mako.discovery.HostSweepStatus
import ninja.mako.discovery.TcpHostSweepRunner
import ninja.mako.network.NetworkIdentityFactory
import ninja.mako.network.NetworkMonitor
import ninja.mako.network.NetworkSnapshot

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
  companion object {
    private const val AUTO_RESCAN_DELAY_MS = 30_000L
  }

  private val networkMonitor = NetworkMonitor(application.applicationContext)
  private val repository = NetworkRepository(application.applicationContext)
  private val hostSweepRunner = TcpHostSweepRunner()
  private val hostEnrichmentResolver = HostEnrichmentResolver(application.applicationContext)
  private val currentNetworkKey = MutableStateFlow<String?>(null)
  private val sweepCycle = MutableStateFlow(0)
  private val sweepSession = MutableStateFlow<HostSweepSession?>(null)
  private val inventoryResults = MutableStateFlow<List<HostProbeResult>>(emptyList())
  private val hostEnrichments = MutableStateFlow<Map<String, HostEnrichment>>(emptyMap())
  private val scanEnabledState = MutableStateFlow(
    ScanPreferences.isEnabled(application.applicationContext)
  )
  private val foregroundActive = MutableStateFlow(true)
  private val filterQuery = MutableStateFlow("")
  private val sortMode = MutableStateFlow(DeviceSortMode.RECENT)
  private val openServiceOnly = MutableStateFlow(false)
  private val infrastructureOnly = MutableStateFlow(false)
  private val webInterfacesOnly = MutableStateFlow(false)
  private var sweepJob: Job? = null
  private var activeSweepSignature: String? = null
  private val enrichmentJobs = linkedMapOf<String, Job>()

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

  private val discoveryPlan = combine(networkSnapshots, sweepCycle) { snapshot, cycle ->
    HostCandidatePlanner.buildPlan(snapshot, scanCycle = cycle)
  }
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
          sweepCycle.value = 0
          inventoryResults.value = emptyList()
          clearHostEnrichments()
          previousNetworkKey = null
          return@collect
        }

        val isActivation = previousNetworkKey != identity.networkKey
        if (isActivation) {
          sweepCycle.value = 0
          inventoryResults.value = emptyList()
          clearHostEnrichments()
        }
        repository.persistSnapshot(identity, snapshot, isActivation)
        currentNetworkKey.value = identity.networkKey
        previousNetworkKey = identity.networkKey
      }
    }

    viewModelScope.launch {
      combine(
        currentNetworkKey,
        discoveryPlan,
        scanEnabledState,
        foregroundActive,
        sweepCycle
      ) { networkKey, plan, scanEnabled, isForeground, cycle ->
        SweepInputs(
          networkKey = networkKey,
          discoveryPlan = plan,
          scanEnabled = scanEnabled,
          foregroundActive = isForeground,
          sweepCycle = cycle
        )
      }.collect { inputs ->
        val stableNetworkKey = inputs.networkKey
        val stablePlan = inputs.discoveryPlan
        val signature =
          if (!inputs.scanEnabled || !inputs.foregroundActive || stableNetworkKey == null || stablePlan == null) {
            null
          } else {
            buildSweepSignature(stableNetworkKey, stablePlan, inputs.sweepCycle)
          }

        if (signature == null) {
          cancelSweep()
          if (stableNetworkKey == null || stablePlan == null) {
            sweepSession.value = null
          }
          return@collect
        }

        if (signature == activeSweepSignature) return@collect

        cancelSweep()
        activeSweepSignature = signature
        sweepSession.value = HostSweepSession.running(stableNetworkKey!!, stablePlan!!, hostSweepRunner.ports())
        sweepJob = viewModelScope.launch {
          try {
            val completedSession = hostSweepRunner.run(stableNetworkKey, stablePlan) { progress ->
              sweepSession.value = progress
              inventoryResults.value = mergeResponsiveResults(inventoryResults.value, progress.results)
            }
            sweepSession.value = completedSession
            if (
              scanEnabledState.value &&
              foregroundActive.value &&
              currentNetworkKey.value == stableNetworkKey
            ) {
              delay(AUTO_RESCAN_DELAY_MS)
              if (
                scanEnabledState.value &&
                foregroundActive.value &&
                currentNetworkKey.value == stableNetworkKey
              ) {
                sweepCycle.value = sweepCycle.value + 1
              }
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

    viewModelScope.launch {
      var previousNetworkKey: String? = null

      combine(networkSnapshots, currentNetworkKey, inventoryResults) { snapshot, networkKey, results ->
        EnrichmentInputs(
          snapshot = snapshot,
          networkKey = networkKey,
          results = results
        )
      }.collect { inputs ->
        if (inputs.networkKey != previousNetworkKey) {
          clearHostEnrichments()
          previousNetworkKey = inputs.networkKey
        }

        if (inputs.networkKey == null || !inputs.snapshot.connected || !inputs.snapshot.isWifi) {
          clearHostEnrichments()
          return@collect
        }

        val activeHosts = inputs.results.map { it.host }.toSet()
        hostEnrichments.value = hostEnrichments.value.filterKeys { host -> host in activeHosts }

        val staleHosts = enrichmentJobs.keys.filter { host -> host !in activeHosts }
        staleHosts.forEach { host ->
          enrichmentJobs.remove(host)?.cancel()
        }

        inputs.results.forEach { result ->
          if (hostEnrichments.value.containsKey(result.host) || enrichmentJobs.containsKey(result.host)) {
            return@forEach
          }

          val stableNetworkKey = inputs.networkKey
          val stableSnapshot = inputs.snapshot
          enrichmentJobs[result.host] = viewModelScope.launch {
            val enrichment = withContext(Dispatchers.IO) {
              hostEnrichmentResolver.resolve(stableSnapshot, result)
            }
            enrichmentJobs.remove(result.host)
            if (currentNetworkKey.value != stableNetworkKey) return@launch
            hostEnrichments.value = hostEnrichments.value + (result.host to enrichment)
          }
        }
      }
    }
  }

  private val detectedDevices = combine(
    networkSnapshots,
    currentNetworkKey,
    inventoryResults,
    hostEnrichments
  ) { snapshot, networkKey, results, enrichments ->
    withContext(Dispatchers.Default) {
      DetectedDeviceInventoryBuilder.build(snapshot, networkKey, results, enrichments)
    }
  }
    .conflate()
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
      initialValue = emptyList()
    )

  val scanEnabled: StateFlow<Boolean> = scanEnabledState

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

  val deviceListSummary: StateFlow<String> = combine(
    detectedDevices,
    devices,
    liveTicker,
    scanEnabledState
  ) { rawDevices, visibleDevices, now, scanEnabled ->
    when {
      rawDevices.isEmpty() && !scanEnabled -> "Discovery paused."
      rawDevices.isEmpty() -> "No local Wi-Fi devices in inventory yet."
      visibleDevices.isEmpty() -> {
        if (scanEnabled) {
          "No hosts match the current filters."
        } else {
          "No hosts match the current filters. Scan paused."
        }
      }
      visibleDevices.size == rawDevices.size -> {
        val liveCount = rawDevices.count { device ->
          device.isLocalDevice || LiveHostWindow.isLive(device.lastSeen, now)
        }
        val trailingStatus =
          if (scanEnabled) {
            "$liveCount active now"
          } else {
            "scan paused"
          }
        "${rawDevices.size} ${deviceLabel(rawDevices.size)} in local inventory • $trailingStatus"
      }
      else -> {
        val base = "Showing ${visibleDevices.size} of ${rawDevices.size} ${deviceLabel(rawDevices.size)}"
        if (scanEnabled) base else "$base • scan paused"
      }
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
    UiStateInputs(
      snapshot = snapshot,
      record = record,
      knownNetworkCount = count,
      discoveryPlan = activeDiscoveryPlan,
      sweepSession = activeSweepSession,
      scanEnabled = true
    )
  }
    .combine(scanEnabledState) { inputs, scanEnabled ->
      inputs.copy(scanEnabled = scanEnabled)
    }
    .combine(detectedDevices) { inputs, devices ->
      MainUiState.from(
        snapshot = inputs.snapshot,
        record = inputs.record,
        knownNetworkCount = inputs.knownNetworkCount,
        discoveryPlan = inputs.discoveryPlan,
        sweepSession = inputs.sweepSession,
        scanEnabled = inputs.scanEnabled,
        inventoryDeviceCount = devices.size
      )
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

  fun startScan() {
    ScanPreferences.setEnabled(getApplication(), true)
    scanEnabledState.value = true
  }

  fun stopScan() {
    ScanPreferences.setEnabled(getApplication(), false)
    scanEnabledState.value = false
  }

  fun setForegroundActive(active: Boolean) {
    foregroundActive.value = active
  }

  fun rescanDiscovery() {
    if (currentNetworkKey.value != null) {
      ScanPreferences.setEnabled(getApplication(), true)
      scanEnabledState.value = true
      sweepCycle.value = sweepCycle.value + 1
    }
  }

  private suspend fun cancelSweep() {
    sweepJob?.cancelAndJoin()
    sweepJob = null
    activeSweepSignature = null
  }

  private fun clearHostEnrichments() {
    enrichmentJobs.values.forEach { job -> job.cancel() }
    enrichmentJobs.clear()
    hostEnrichments.value = emptyMap()
  }

  private fun buildSweepSignature(networkKey: String, plan: HostDiscoveryPlan, sweepCycle: Int): String {
    return buildString {
      append(networkKey)
      append("|")
      append(sweepCycle)
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

  private fun mergeResponsiveResults(
    existing: List<HostProbeResult>,
    results: List<HostProbeResult>
  ): List<HostProbeResult> {
    if (results.isEmpty()) return existing

    val merged = existing.associateByTo(linkedMapOf()) { it.host }
    results
      .asSequence()
      .filter(::isResponsive)
      .forEach { result ->
        val previous = merged[result.host]
        if (previous == null || result.observedAt >= previous.observedAt) {
          merged[result.host] = result
        }
      }

    return merged.values.sortedBy { it.observedAt }
  }

  private fun isResponsive(result: HostProbeResult): Boolean {
    return result.outcome == HostProbeOutcome.CONNECTED || result.outcome == HostProbeOutcome.REFUSED
  }

  private object LiveHostWindow {
    const val WINDOW_MS = 120_000L
    const val TICK_MS = 5_000L

    fun isLive(lastSeen: Long, now: Long): Boolean {
      return now - lastSeen <= WINDOW_MS
    }
  }

  private data class UiStateInputs(
    val snapshot: NetworkSnapshot,
    val record: ninja.mako.data.NetworkRecordEntity?,
    val knownNetworkCount: Int,
    val discoveryPlan: HostDiscoveryPlan?,
    val sweepSession: HostSweepSession?,
    val scanEnabled: Boolean
  )

  private data class SweepInputs(
    val networkKey: String?,
    val discoveryPlan: HostDiscoveryPlan?,
    val scanEnabled: Boolean,
    val foregroundActive: Boolean,
    val sweepCycle: Int
  )

  private data class EnrichmentInputs(
    val snapshot: NetworkSnapshot,
    val networkKey: String?,
    val results: List<HostProbeResult>
  )
}
