package ninja.mako.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import ninja.mako.R
import ninja.mako.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
  private lateinit var binding: ActivityMainBinding
  private val viewModel: MainViewModel by viewModels()
  private var toolbarBaseTopPadding = 0
  private var scrollBaseBottomPadding = 0
  private var latestDiagnosticsReport = ""

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    setSupportActionBar(binding.toolbar)
    supportActionBar?.title = getString(R.string.app_name_header)
    supportActionBar?.subtitle = getString(R.string.app_subtitle)

    WindowCompat.setDecorFitsSystemWindows(window, false)
    toolbarBaseTopPadding = binding.toolbar.paddingTop
    scrollBaseBottomPadding = binding.scrollContent.paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      binding.toolbar.updatePadding(top = toolbarBaseTopPadding + bars.top)
      binding.scrollContent.updatePadding(bottom = scrollBaseBottomPadding + bars.bottom)
      insets
    }

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState.collect(::render)
      }
    }

    binding.openDiagnosticsButton.setOnClickListener {
      startActivity(DiagnosticsActivity.intent(this, latestDiagnosticsReport))
    }
  }

  private fun render(state: MainUiState) {
    binding.eyebrow.text = state.eyebrow
    binding.headline.text = state.headline
    binding.subhead.text = state.subhead
    binding.statusBadge.text = state.statusBadge

    binding.transportValue.text = state.transport
    binding.interfaceValue.text = state.interfaceName
    binding.localAddressValue.text = state.localAddress
    binding.subnetValue.text = state.subnet
    binding.gatewayValue.text = state.gateway
    binding.dnsValue.text = state.dnsServers
    binding.domainsValue.text = state.domains
    binding.privateDnsValue.text = state.privateDns
    binding.validationValue.text = state.validation

    binding.discoverySummary.text = state.discoverySummary
    binding.networkMemorySummary.text = state.networkMemorySummary
    latestDiagnosticsReport = state.diagnosticsReport

    binding.wifiWarning.isVisible = state.showWifiWarning
    binding.wifiWarning.text = state.wifiWarning
  }
}
