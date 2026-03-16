package ninja.mako.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import android.view.View
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ninja.mako.R
import ninja.mako.databinding.ActivityDeviceDetailBinding
import ninja.mako.discovery.ManualPortScanner

class DeviceDetailActivity : AppCompatActivity() {
  private lateinit var binding: ActivityDeviceDetailBinding
  private var toolbarBaseTopPadding = 0
  private var scrollBaseBottomPadding = 0

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityDeviceDetailBinding.inflate(layoutInflater)
    setContentView(binding.root)

    val detail = loadDetail() ?: run {
      finish()
      return
    }

    setSupportActionBar(binding.toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    supportActionBar?.title = detail.title

    binding.deviceName.text = detail.title
    binding.deviceBadge.text = detail.badgeLabel
    binding.deviceHost.text = detail.hostAddress
    binding.deviceMeta.text = detail.metaLine
    binding.deviceStatus.text = detail.statusLine
    binding.reportText.text = detail.report
    binding.copyButton.setOnClickListener {
      val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      clipboard.setPrimaryClip(ClipData.newPlainText("MAKO device detail", detail.report))
      Toast.makeText(this, R.string.device_detail_copied, Toast.LENGTH_SHORT).show()
    }

    binding.scanPortsButton.setOnClickListener {
      binding.scanPortsButton.isEnabled = false
      binding.scanPortsResult.visibility = View.VISIBLE
      binding.scanPortsResult.text = getString(R.string.port_scan_in_progress, ManualPortScanner.EXTENDED_PORTS.size)
      
      lifecycleScope.launch {
        try {
          val openPorts = ManualPortScanner.scanPorts(detail.hostAddress)
          if (openPorts.isEmpty()) {
            binding.scanPortsResult.text = getString(R.string.port_scan_none)
          } else {
            val ssb = android.text.SpannableStringBuilder("Open ports:\n")
            openPorts.forEach { result ->
              val lineStart = ssb.length
              
              if (result.isHttp || result.isHttps) {
                val proto = if (result.isHttps) "https" else "http"
                val url = "$proto://${detail.hostAddress}:${result.port}"
                ssb.append(url)
                ssb.setSpan(
                  android.text.style.URLSpan(url),
                  lineStart,
                  ssb.length,
                  android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
              } else {
                ssb.append("TCP ${result.port}")
              }
              
              if (!result.banner.isNullOrBlank()) {
                ssb.append(" — ${result.banner}")
              }
              ssb.append("\n")
            }
            binding.scanPortsResult.text = ssb.trim()
            binding.scanPortsResult.movementMethod = android.text.method.LinkMovementMethod.getInstance()
          }
        } catch (e: Exception) {
          binding.scanPortsResult.text = getString(R.string.port_scan_failed)
        } finally {
          binding.scanPortsButton.isEnabled = true
        }
      }
    }

    WindowCompat.setDecorFitsSystemWindows(window, false)
    toolbarBaseTopPadding = binding.toolbar.paddingTop
    scrollBaseBottomPadding = binding.scrollContent.paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      binding.toolbar.updatePadding(top = toolbarBaseTopPadding + bars.top)
      binding.scrollContent.updatePadding(bottom = scrollBaseBottomPadding + bars.bottom)
      insets
    }
  }

  override fun onSupportNavigateUp(): Boolean {
    finish()
    return true
  }

  private fun loadDetail(): DiscoveredDeviceDetail? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getSerializableExtra(EXTRA_DETAIL, DiscoveredDeviceDetail::class.java)
    } else {
      @Suppress("DEPRECATION")
      intent.getSerializableExtra(EXTRA_DETAIL) as? DiscoveredDeviceDetail
    }
  }

  companion object {
    private const val EXTRA_DETAIL = "detail"

    fun intent(context: Context, detail: DiscoveredDeviceDetail): Intent {
      return Intent(context, DeviceDetailActivity::class.java)
        .putExtra(EXTRA_DETAIL, detail)
    }
  }
}
