package me.proton.android.calendar.common.logger

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object LogExporter {

    const val EXPORT_FILE_NAME = "calendar-logs.zip"

    private const val LOGCAT_CUTOFF_SECONDS = 12 * 3600L

    fun logDir(context: Context): File = File(context.cacheDir, "calendar_logs")

    fun buildZip(context: Context): File? {
        val exportDir = File(context.cacheDir, "log_export").apply {
            deleteRecursively()
            mkdirs()
        }

        val files = logDir(context).listFiles()?.filter { it.isFile }.orEmpty() +
            listOfNotNull(dumpLogcat(exportDir))
        if (files.isEmpty()) return null

        val zip = File(exportDir, EXPORT_FILE_NAME)
        ZipOutputStream(FileOutputStream(zip)).use { zos ->
            files.forEach { file ->
                zos.putNextEntry(ZipEntry(file.name))
                FileInputStream(file).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return zip
    }

    fun buildShareIntent(context: Context): Intent? {
        val zip = buildZip(context) ?: return null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.logprovider", zip)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun copyToUri(context: Context, src: File, dest: Uri) {
        context.contentResolver.openOutputStream(dest)?.use { out ->
            FileInputStream(src).use { it.copyTo(out) }
        }
    }

    private fun dumpLogcat(exportDir: File): File? = runCatching {
        val out = File(exportDir, "logcat.txt")
        // --t cutoff bounds the dump to recent history so the buffer can't produce an excessively large file
        val process = Runtime.getRuntime().exec(
            arrayOf("logcat", "-d", "-v", "time", "*:V", "--t", logcatCutoffTimestamp())
        )
        try {
            process.inputStream.bufferedReader().use { reader ->
                out.bufferedWriter().use { writer ->
                    reader.lineSequence().forEach { line ->
                        if (!isNoisyLogcatLine(line)) writer.appendLine(line)
                    }
                }
            }
            process.waitFor()
        } finally {
            process.destroy()
        }
        out
    }.getOrNull()

    private fun logcatCutoffTimestamp(): String =
        DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.000")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now().minusSeconds(LOGCAT_CUTOFF_SECONDS))

    private val logcatTagRegex = Regex("""\s[VDIWEF]/([^(]+)\(""")

    private fun isNoisyLogcatLine(line: String): Boolean {
        val tag = logcatTagRegex.find(line)?.groupValues?.get(1)?.trim() ?: return false
        return NOISY_TAG_PREFIXES.any { tag.startsWith(it) }
    }

    private val NOISY_TAG_PREFIXES = listOf(
        "SpannableStringBuilder", "ViewRootImpl", "OpenGLRenderer",
        "AdrenoGLES", "BLASTBufferQueue", "BufferQueueProducer", "InsetsController",
        "InsetsSourceConsumer", "InputMethodManager", "InputTransport", "ImeFocusController",
        "ImeTracker", "Choreographer", "DecorView", "CompatibilityChangeReporter",
        "GraphicsEnvironment", "nativeloader", "WindowOnBackDispatcher", "WindowManager",
        "PopupWindow", "AutofillManager", "AnimatorSet", "NativeCustomFrequencyManager",
    )
}
