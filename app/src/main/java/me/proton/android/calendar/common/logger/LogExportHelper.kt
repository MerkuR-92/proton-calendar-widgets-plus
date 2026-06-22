package me.proton.android.calendar.common.logger

import android.content.Intent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import java.io.File

class LogExportHelper(private val fragment: Fragment) {

    private var pendingZip: File? = null

    private val saveLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val src = pendingZip
        pendingZip = null
        if (uri == null || src == null) return@registerForActivityResult
        val ctx = fragment.requireContext()
        fragment.lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) { runCatching { LogExporter.copyToUri(ctx, src, uri) }.isSuccess }
            toast(if (saved) R.string.settings_logs_saved else R.string.settings_logs_save_failed)
        }
    }

    fun share() {
        val ctx = fragment.requireContext()
        fragment.lifecycleScope.launch {
            val intent = withContext(Dispatchers.IO) { runCatching { LogExporter.buildShareIntent(ctx) }.getOrNull() }
            if (intent == null) {
                toast(R.string.settings_logs_none)
                return@launch
            }
            fragment.startActivity(Intent.createChooser(intent, fragment.getString(R.string.settings_share_logs)))
        }
    }

    fun saveToDisk() {
        val ctx = fragment.requireContext()
        fragment.lifecycleScope.launch {
            val zip = withContext(Dispatchers.IO) { runCatching { LogExporter.buildZip(ctx) }.getOrNull() }
            if (zip == null) {
                toast(R.string.settings_logs_none)
                return@launch
            }
            pendingZip = zip
            saveLauncher.launch(LogExporter.EXPORT_FILE_NAME)
        }
    }

    private fun toast(@StringRes resId: Int) {
        Toast.makeText(fragment.requireContext(), fragment.getString(resId), Toast.LENGTH_SHORT).show()
    }
}
