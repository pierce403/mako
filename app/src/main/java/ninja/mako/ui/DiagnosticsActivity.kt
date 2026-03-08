package ninja.mako.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import ninja.mako.R
import ninja.mako.databinding.ActivityDiagnosticsBinding

class DiagnosticsActivity : AppCompatActivity() {
  private lateinit var binding: ActivityDiagnosticsBinding
  private var toolbarBaseTopPadding = 0
  private var scrollBaseBottomPadding = 0

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityDiagnosticsBinding.inflate(layoutInflater)
    setContentView(binding.root)

    setSupportActionBar(binding.toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    supportActionBar?.title = getString(R.string.diagnostics)

    val report = intent.getStringExtra(EXTRA_REPORT).orEmpty()
    binding.reportText.text = report
    binding.copyButton.setOnClickListener {
      val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      clipboard.setPrimaryClip(ClipData.newPlainText("MAKO diagnostics", report))
      Toast.makeText(this, R.string.diagnostics_copied, Toast.LENGTH_SHORT).show()
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

  companion object {
    private const val EXTRA_REPORT = "report"

    fun intent(context: Context, report: String): Intent {
      return Intent(context, DiagnosticsActivity::class.java)
        .putExtra(EXTRA_REPORT, report)
    }
  }
}
