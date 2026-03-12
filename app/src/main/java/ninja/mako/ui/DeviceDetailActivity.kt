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
import ninja.mako.R
import ninja.mako.databinding.ActivityDeviceDetailBinding

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
