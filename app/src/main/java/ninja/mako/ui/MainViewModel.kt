package ninja.mako.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ninja.mako.data.NetworkRepository
import ninja.mako.network.NetworkIdentityFactory
import ninja.mako.network.NetworkMonitor

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
  private val networkMonitor = NetworkMonitor(application.applicationContext)
  private val repository = NetworkRepository(application.applicationContext)
  private val currentNetworkKey = MutableStateFlow<String?>(null)

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
  }

  val uiState: StateFlow<MainUiState> = combine(
    networkSnapshots,
    currentNetworkRecord,
    knownNetworkCount
  ) { snapshot, record, count ->
    MainUiState.from(snapshot, record, count)
  }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
      initialValue = MainUiState()
    )
}
