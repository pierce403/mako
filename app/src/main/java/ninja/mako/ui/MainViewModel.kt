package ninja.mako.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ninja.mako.data.NetworkRepository
import ninja.mako.discovery.HostCandidatePlanner
import ninja.mako.discovery.HostSweepSession
import ninja.mako.discovery.HostSweepStatus
import ninja.mako.discovery.TcpHostSweepRunner
import ninja.mako.network.NetworkIdentityFactory
import ninja.mako.network.NetworkMonitor

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
  private val networkMonitor = NetworkMonitor(application.applicationContext)
  private val repository = NetworkRepository(application.applicationContext)
  private val hostSweepRunner = TcpHostSweepRunner()
  private val currentNetworkKey = MutableStateFlow<String?>(null)
  private val sweepSession = MutableStateFlow<HostSweepSession?>(null)
  private var sweepJob: Job? = null
  private var activeSweepSignature: String? = null

  private val networkSnapshots = networkMonitor
    .snapshots()
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
      initialValue = ninja.mako.network.NetworkSnapshot()
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
            sweepSession.value = hostSweepRunner.run(stableNetworkKey, stablePlan)
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

  val uiState: StateFlow<MainUiState> = combine(
    networkSnapshots,
    currentNetworkRecord,
    knownNetworkCount,
    discoveryPlan,
    sweepSession
  ) { snapshot, record, count, discoveryPlan, sweepSession ->
    MainUiState.from(snapshot, record, count, discoveryPlan, sweepSession)
  }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
      initialValue = MainUiState()
    )

  private suspend fun cancelSweep() {
    sweepJob?.cancelAndJoin()
    sweepJob = null
    activeSweepSignature = null
  }

  private fun buildSweepSignature(networkKey: String, plan: ninja.mako.discovery.HostDiscoveryPlan): String {
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
}
