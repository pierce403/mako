package ninja.mako.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import ninja.mako.R
import ninja.mako.databinding.ActivityMainBinding
import ninja.mako.discovery.ContinuousScanPreferences
import ninja.mako.discovery.ContinuousScanService
import ninja.mako.discovery.HostSweepStatus

class MainActivity : AppCompatActivity() {
  private val notificationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (!pendingNotificationPermissionFlow) return@registerForActivityResult
    pendingNotificationPermissionFlow = false
    Toast.makeText(
      this,
      if (granted) R.string.notifications_enabled else R.string.notifications_denied,
      Toast.LENGTH_SHORT
    ).show()
  }

  private lateinit var binding: ActivityMainBinding
  private val viewModel: MainViewModel by viewModels()
  private lateinit var adapter: DeviceAdapter
  private var toolbarBaseTopPadding = 0
  private var deviceListBaseBottomPadding = 0
  private var filterDrawerBaseTopPadding = 0
  private var filterDrawerBaseBottomPadding = 0
  private var latestDiagnosticsReport = ""
  private var latestListSummary = ""
  private var latestUiState = MainUiState()
  private var latestScanEnabled = true
  private var latestVisibleDeviceCount = 0
  private var continuousScanningEnabled = false
  private var pendingNotificationPermissionFlow = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    continuousScanningEnabled = ContinuousScanPreferences.isEnabled(this)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    setSupportActionBar(binding.toolbar)
    supportActionBar?.title = getString(R.string.app_name_header)
    supportActionBar?.subtitle = getString(R.string.device_list_summary_default)
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
            latestListSummary = summary
            updateToolbarSubtitle()
          }
        }
        launch {
          viewModel.scanEnabled.collect { enabled ->
            latestScanEnabled = enabled
            invalidateOptionsMenu()
            updateEmptyState()
          }
        }
      }
    }
  }

  override fun onStart() {
    super.onStart()
    continuousScanningEnabled = ContinuousScanPreferences.isEnabled(this)
    viewModel.setForegroundActive(true)
    ContinuousScanService.stop(this)
    invalidateOptionsMenu()
  }

  override fun onStop() {
    viewModel.setForegroundActive(false)
    if (!isChangingConfigurations && continuousScanningEnabled && latestScanEnabled) {
      ContinuousScanService.start(this)
    }
    super.onStop()
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.main_menu, menu)
    syncMenuState(menu)
    return true
  }

  override fun onPrepareOptionsMenu(menu: Menu): Boolean {
    syncMenuState(menu)
    return super.onPrepareOptionsMenu(menu)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      R.id.menu_scan_toggle -> {
        if (latestScanEnabled) {
          stopCurrentScan()
        } else {
          startCurrentScan()
        }
        true
      }
      R.id.menu_rescan -> {
        viewModel.rescanDiscovery()
        Toast.makeText(this, R.string.rescan_requested, Toast.LENGTH_SHORT).show()
        true
      }
      R.id.menu_continuous_scanning -> {
        val enabled = !item.isChecked
        item.isChecked = enabled
        setContinuousScanningEnabled(enabled)
        true
      }
      R.id.menu_diagnostics -> {
        openDiagnostics()
        true
      }
      R.id.menu_copy_diagnostics -> {
        copyDiagnosticsReport()
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }

  private fun render(state: MainUiState) {
    latestUiState = state
    latestScanEnabled = state.scanEnabled
    binding.statusBadge.text = state.statusBadge
    binding.drawerStatus.text = buildDrawerStatus(state)
    binding.networkScopeSummary.text = buildNetworkScopeSummary(state)
    binding.discoverySummary.text = state.discoverySummary
    latestDiagnosticsReport = state.diagnosticsReport

    binding.wifiWarning.isVisible = state.showWifiWarning
    binding.wifiWarning.text = state.wifiWarning
    updateToolbarSubtitle()
    invalidateOptionsMenu()
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
      add(if (state.showWifiWarning) state.headline else state.transport)
      state.localAddress.takeUnless { it == "Unavailable" }?.let { localAddress -> add("Local $localAddress") }
      state.subnet.takeUnless { it == "Unavailable" }?.let(::add)
      state.gateway.takeUnless { it == "Unavailable" }?.let { gateway -> add("Gateway $gateway") }
      if (state.validation.isNotBlank() && state.validation != "No active validation state") {
        add(state.validation)
      }
    }.joinToString("  •  ")
  }

  private fun buildDrawerStatus(state: MainUiState): String {
    return when {
      state.showWifiWarning -> getString(R.string.status_offline)
      !state.scanEnabled -> getString(R.string.drawer_status_paused)
      state.sweepStatus == HostSweepStatus.RUNNING -> getString(R.string.drawer_status_running)
      state.sweepStatus == HostSweepStatus.COMPLETED -> getString(R.string.drawer_status_complete)
      state.sweepStatus == HostSweepStatus.FAILED -> getString(R.string.drawer_status_failed)
      state.sweepStatus == HostSweepStatus.CANCELLED -> getString(R.string.drawer_status_cancelled)
      else -> state.statusBadge
    }
  }

  private fun updateEmptyState() {
    val message = when {
      latestUiState.showWifiWarning -> getString(R.string.empty_state_wifi_required)
      latestVisibleDeviceCount > 0 -> ""
      latestUiState.totalDetectedDevices > 0 -> getString(R.string.empty_state_filters)
      !latestUiState.scanEnabled -> getString(R.string.empty_state_scan_paused)
      latestUiState.sweepStatus == HostSweepStatus.RUNNING -> getString(R.string.empty_state_scanning)
      latestUiState.sweepStatus == HostSweepStatus.FAILED -> getString(R.string.empty_state_failed)
      latestUiState.sweepStatus == HostSweepStatus.CANCELLED -> getString(R.string.empty_state_cancelled)
      else -> getString(R.string.empty_state_no_devices)
    }

    binding.emptyState.isVisible = latestVisibleDeviceCount == 0
    binding.emptyState.text = message
  }

  private fun updateToolbarSubtitle() {
    supportActionBar?.subtitle =
      if (latestUiState.showWifiWarning) {
        latestUiState.wifiWarning
      } else {
        latestListSummary.ifBlank { getString(R.string.device_list_summary_default) }
      }
  }

  private fun syncMenuState(menu: Menu) {
    menu.findItem(R.id.menu_scan_toggle)?.apply {
      title = getString(
        if (latestScanEnabled) {
          R.string.menu_stop_scan
        } else {
          R.string.menu_start_scan
        }
      )
      isEnabled = !latestUiState.showWifiWarning
    }
    menu.findItem(R.id.menu_rescan)?.apply {
      isEnabled = !latestUiState.showWifiWarning
      title = getString(
        if (latestUiState.sweepStatus == HostSweepStatus.RUNNING) {
          R.string.menu_restart_scan
        } else {
          R.string.menu_rescan
        }
      )
    }
    menu.findItem(R.id.menu_continuous_scanning)?.isChecked = continuousScanningEnabled
  }

  private fun startCurrentScan() {
    viewModel.startScan()
    Toast.makeText(this, R.string.scan_started, Toast.LENGTH_SHORT).show()
  }

  private fun stopCurrentScan() {
    viewModel.stopScan()
    ContinuousScanService.stop(this)
    Toast.makeText(this, R.string.scan_stopped, Toast.LENGTH_SHORT).show()
  }

  private fun setContinuousScanningEnabled(enabled: Boolean) {
    if (continuousScanningEnabled == enabled) return

    continuousScanningEnabled = enabled
    ContinuousScanPreferences.setEnabled(this, enabled)
    if (!enabled) {
      ContinuousScanService.stop(this)
    } else {
      maybeRequestNotificationPermissionForContinuousScan()
    }

    Toast.makeText(
      this,
      if (enabled) R.string.continuous_scanning_enabled else R.string.continuous_scanning_disabled,
      Toast.LENGTH_SHORT
    ).show()
    invalidateOptionsMenu()
  }

  private fun maybeRequestNotificationPermissionForContinuousScan() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    if (
      ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
    ) {
      return
    }

    pendingNotificationPermissionFlow = true
    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
  }

  private fun openDiagnostics() {
    startActivity(DiagnosticsActivity.intent(this, buildDiagnosticsReport()))
  }

  private fun copyDiagnosticsReport() {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("MAKO diagnostics", buildDiagnosticsReport()))
    Toast.makeText(this, R.string.diagnostics_copied, Toast.LENGTH_SHORT).show()
  }

  private fun toggleFiltersDrawer() {
    if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
      binding.drawerLayout.closeDrawer(GravityCompat.START)
    } else {
      binding.drawerLayout.openDrawer(GravityCompat.START)
    }
  }

  private fun buildDiagnosticsReport(): String {
    return buildString {
      append(latestDiagnosticsReport.trimEnd())
      appendLine()
      appendLine()
      appendLine("Main screen")
      appendLine("Foreground scan enabled: $latestScanEnabled")
      appendLine("Continuous scanning enabled: $continuousScanningEnabled")
      appendLine("Notification permission: ${notificationPermissionState()}")
    }.trimEnd()
  }

  private fun notificationPermissionState(): String {
    return when {
      Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> "Not required on this Android version"
      ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED -> "Granted"
      else -> "Not granted"
    }
  }
}
