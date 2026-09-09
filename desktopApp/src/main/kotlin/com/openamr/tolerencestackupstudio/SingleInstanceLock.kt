package com.openamr.tolerencestackupstudio

import java.io.File
import java.util.Locale

/**
 * Prevents two copies of the app running at once, with a message that's
 * actually TRUE and actually ACTIONABLE.
 */
object SingleInstanceLock {

    private fun lockFile(): File {
        val os = System.getProperty("os.name").lowercase(Locale.ROOT)
        val appData = when {
            os.contains("win") -> File(System.getenv("APPDATA") ?: System.getProperty("user.home"))
            os.contains("mac") -> File(System.getProperty("user.home"), "Library/Application Support")
            else -> File(System.getProperty("user.home"), ".local/share")
        }
        val dir = File(appData, "tolerance-stackup-studio")
        dir.mkdirs()
        return File(dir, "app.lock")
    }

    sealed class AcquireResult {
        data object Acquired : AcquireResult()
        data class AlreadyRunning(val pid: Long) : AcquireResult()
    }

    /** Call once at startup, before building any UI. */
    fun acquire(): AcquireResult {
        val file = lockFile()

        if (file.exists()) {
            val existingPid = file.readText().trim().toLongOrNull()
            val stillAlive = existingPid != null &&
                ProcessHandle.of(existingPid).map { it.isAlive }.orElse(false)

            if (stillAlive) {
                return AcquireResult.AlreadyRunning(existingPid!!)
            }
            // Stale lock from an abnormal previous exit — clean up and continue.
            file.delete()
        }

        file.writeText(ProcessHandle.current().pid().toString())
        Runtime.getRuntime().addShutdownHook(Thread { release() })
        return AcquireResult.Acquired
    }

    fun release() {
        val file = lockFile()
        if (file.exists() && file.readText().trim().toLongOrNull() == ProcessHandle.current().pid()) {
            file.delete()
        }
    }
}
