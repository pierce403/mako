package ninja.mako.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import ninja.mako.R
import ninja.mako.databinding.ActivityMainBinding
import ninja.mako.discovery.HostSweepStatus

class MainActivity : AppCompatActivity() {
  private lateinit var binding: ActivityMainBinding
  private val viewModel: MainViewModel by viewModels()
  private lateinit var adapter: DeviceAdapter
  private var toolbarBaseTopPadding = 0
  private var deviceListBaseBottomPadding = 0
  private var filterDrawerBaseTopPadding = 0
  private var filterDrawerBaseBottomPadding = 0
  private var latestDiagnosticsReport = ""
  private var latestUiState = MainUiState()
  private var latestVisibleDeviceCount = 0

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    setSupportActionBar(binding.toolbar)
    supportActionBar?.title = getString(R.string.app_name_header)
    supportActionBar?.subtitle = getString(R.string.app_subtitle)
    binding.toolbar.setNavigationIcon(R.drawable.ic_filter_list)
    binding.toolbar.navigationContentDescription = getString(R.string.open_filters)
    binding.toolbar.setNavigationOnClickListener {
      toggleFiltersDrawer()
    }

    WindowCompat.setDecorFitsSystemWindows(window, false)
    toolbarBaseTopPadding = binding.toolbar.paddingTop
    deviceListBaseBottomPadding = binding.deviceList.paddingBottom
    filterDrawerBaseTopPadding = binding.filterDrawerContent.paddingTop
    filterDrawerBaseBottomPadding = binding.filterDrawerContent.paddingBottom

    adapter = DeviceAdapter()
    binding.deviceList.layoutManager = LinearLayoutManager(this)
    binding.deviceList.adapter = adapter
    binding.deviceList.itemAnimator = null

    val sortAdapter = ArrayAdapter(
      this,
      R.layout.spinner_item,
      DeviceSortMode.values().map { it.label }
    )
    sortAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
    binding.sortSpinner.adapter = sortAdapter
    binding.sortSpinner.onItemSelectedListener =
      object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
          parent: android.widget.AdapterView<*>?,
          view: android.view.View?,
          position: Int,
          id: Long
        ) {
          viewModel.updateSortMode(DeviceSortMode.values()[position])
        }

        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
      }

    ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      binding.toolbar.updatePadding(top = toolbarBaseTopPadding + bars.top)
      binding.deviceList.updatePadding(bottom = deviceListBaseBottomPadding + bars.bottom)
      binding.filterDrawerContent.updatePadding(
        top = filterDrawerBaseTopPadding + bars.top,
        bottom = filterDrawerBaseBottomPadding + bars.bottom
      )
      insets
    }

    binding.filterInput.doAfterTextChanged { text ->
      viewModel.updateQuery(text?.toString().orEmpty())
    }
    binding.openServiceOnly.setOnCheckedChangeListener { _, isChecked ->
      viewModel.setOpenServiceOnly(isChecked)
    }
    binding.infrastructureOnly.setOnCheckedChangeListener { _, isChecked ->
      viewModel.setInfrastructureOnly(isChecked)
    }
    binding.webInterfacesOnly.setOnCheckedChangeListener { _, isChecked ->
      viewModel.setWebInterfacesOnly(isChecked)
    }
    binding.openDiagnosticsButton.setOnClickListener {
      startActivity(DiagnosticsActivity.intent(this, latestDiagnosticsReport))
    }

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch {
          viewModel.uiState.collect(::render)
        }
        launch {
          viewModel.devices.collect { devices ->
            latestVisibleDeviceCount = devices.size
            adapter.submitList(devices)
            binding.deviceList.isVisible = devices.isNotEmpty()
            updateEmptyState()
          }
        }
        launch {
          viewModel.filterState.collect(::renderFilters)
        }
        launch {
          viewModel.deviceListSummary.collect { summary ->
            binding.listSummary.text = summary
          }
        }
      }
    }
  }

  private fun render(state: MainUiState) {
    latestUiState = state
    binding.eyebrow.text = state.eyebrow
    binding.headline.text = state.headline
    binding.subhead.text = state.subhead
    binding.statusBadge.text = state.statusBadge
    binding.drawerStatus.text = state.statusBadge
    binding.networkScopeSummary.text = buildNetworkScopeSummary(state)
    binding.discoverySummary.text = state.discoverySummary
    binding.networkMemorySummary.text = state.networkMemorySummary
    latestDiagnosticsReport = state.diagnosticsReport

    binding.wifiWarning.isVisible = state.showWifiWarning
    binding.wifiWarning.text = state.wifiWarning
    updateEmptyState()
  }

  private fun renderFilters(state: DeviceFilterState) {
    if (binding.filterInput.text?.toString().orEmpty() != state.query) {
      binding.filterInput.setText(state.query)
      binding.filterInput.setSelection(binding.filterInput.text?.length ?: 0)
    }
    if (binding.sortSpinner.selectedItemPosition != state.sortMode.ordinal) {
      binding.sortSpinner.setSelection(state.sortMode.ordinal)
    }
    if (binding.openServiceOnly.isChecked != state.openServiceOnly) {
      binding.openServiceOnly.isChecked = state.openServiceOnly
    }
    if (binding.infrastructureOnly.isChecked != state.infrastructureOnly) {
      binding.infrastructureOnly.isChecked = state.infrastructureOnly
    }
    if (binding.webInterfacesOnly.isChecked != state.webInterfacesOnly) {
      binding.webInterfacesOnly.isChecked = state.webInterfacesOnly
    }
  }

  private fun buildNetworkScopeSummary(state: MainUiState): String {
    return buildList {
      add(state.transport)
      state.subnet.takeUnless { it == "Unavailable" }?.let(::add)
      state.gateway.takeUnless { it == "Unavailable" }?.let { gateway -> add("Gateway $gateway") }
      if (state.validation.isNotBlank() && state.validation != "No active validation state") {
        add(state.validation)
      }
    }.joinToString("  •  ")
  }

  private fun updateEmptyState() {
    val message = when {
      latestUiState.showWifiWarning -> getString(R.string.empty_state_wifi_required)
      latestVisibleDeviceCount > 0 -> ""
      latestUiState.totalDetectedDevices > 0 -> getString(R.string.empty_state_filters)
      latestUiState.sweepStatus == HostSweepStatus.RUNNING -> getString(R.string.empty_state_scanning)
      latestUiState.sweepStatus == HostSweepStatus.FAILED -> getString(R.string.empty_state_failed)
      latestUiState.sweepStatus == HostSweepStatus.CANCELLED -> getString(R.string.empty_state_cancelled)
      else -> getString(R.string.empty_state_no_devices)
    }

    binding.emptyState.isVisible = latestVisibleDeviceCount == 0
    binding.emptyState.text = message
  }

  private fun toggleFiltersDrawer() {
    if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
      binding.drawerLayout.closeDrawer(GravityCompat.START)
    } else {
      binding.drawerLayout.openDrawer(GravityCompat.START)
    }
  }
}
