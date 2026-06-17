package me.proton.android.calendar.common.logger

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes Timber logs to rotating files. Writes are dispatched to a single background thread and use one
 * persistent [FileWriter] (append + flush), so logging never blocks the calling thread (incl. the main
 * and decryption threads) and therefore does not perturb the timings we measure. Mirrors the approach
 * used by Proton Mail's LogsFileHandler.
 */
class LogFileTree(
    private val dir: File,
    private val excludedTags: Set<String> = emptySet(),
) : Timber.Tree() {

    // limitedParallelism(1): serial, off-main writes — preserves ordering and guards the FileWriter.
    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob())
    private val lineStamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    private var currentLogFile: File? = null
    private var fileWriter: FileWriter? = null

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (tag != null && tag in excludedTags) return
        val logTag = tag ?: APP_TAG
        // Capture only cheap values on the calling thread; format + write on the single writer thread.
        val timeMillis = System.currentTimeMillis()
        val priorityChar = priorityChar(priority)
        scope.launch {
            runCatching {
                val writer = fileWriter ?: prepareFileWriter()
                writer.appendLine("${lineStamp.format(Date(timeMillis))} $priorityChar/$logTag: $message")
                if (t != null) writer.appendLine(Log.getStackTraceString(t))
                writer.flush()
                currentLogFile?.takeIf { it.length() > MAX_FILE_BYTES }?.let { rotate() }
            }
        }
    }

    private fun prepareFileWriter(): FileWriter {
        if (!dir.exists()) dir.mkdirs()
        val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() }.orEmpty()
        currentLogFile = when {
            files.isEmpty() -> createNewFile()
            files.last().length() > MAX_FILE_BYTES -> createNewFile()
            else -> files.last()
        }
        return FileWriter(currentLogFile, true).also { fileWriter = it }
    }

    private fun rotate() {
        runCatching { fileWriter?.close() }
        fileWriter = null
        val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() }.orEmpty()
        if (files.size >= MAX_FILES) {
            files.take(files.size - MAX_FILES + 1).forEach { it.delete() }
        }
        prepareFileWriter()
    }

    private fun createNewFile(): File =
        File(dir, "calendar-${fileStamp.format(Date())}.log").apply { runCatching { createNewFile() } }

    private fun priorityChar(priority: Int): Char = when (priority) {
        Log.VERBOSE -> 'V'
        Log.DEBUG -> 'D'
        Log.INFO -> 'I'
        Log.WARN -> 'W'
        Log.ERROR -> 'E'
        Log.ASSERT -> 'A'
        else -> '?'
    }

    companion object {
        private const val MAX_FILE_BYTES = 2 * 1024 * 1024 // 2 MB
        private const val MAX_FILES = 5
        private val fileStamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

        const val APP_TAG = "app"
    }
}
