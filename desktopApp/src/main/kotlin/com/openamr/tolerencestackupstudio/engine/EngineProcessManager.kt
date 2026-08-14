package com.openamr.tolerencestackupstudio.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.net.Socket
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Owns the lifecycle of the engine subprocess:
 *   locate exe -> launch -> read READY handshake line from stdout -> open socket.
 *
 * Two run modes:
 *  - PACKAGED: uses the PyInstaller-frozen executable bundled under
 *    resources/engine-dist/<os>-<arch>/tolerance-engine(.exe). This is what
 *    ships to end users (Phase 5) — no Python installation required on their
 *    machine.
 *  - DEV (fallback used automatically when no packaged exe is found): shells
 *    out to `python -m tolerance_engine` against the engine/ source tree, so
 *    Phase 1-4 development doesn't require re-running PyInstaller on every
 *    change to Python code.
 */
class EngineProcessManager {

    private var process: Process? = null
    private val readyMarker = Pattern.compile("^TOLERANCE_ENGINE_READY (\\{.*})$")

    suspend fun start(): EngineTransport = withContext(Dispatchers.IO) {
        val builder = resolveProcessBuilder()
        builder.redirectErrorStream(false)
        val proc = builder.start()
        process = proc

        // Free-form Python stderr (library warnings, tracebacks) is logged,
        // never parsed — the protocol lives on the socket, not on stdio.
        Thread(StderrLogger(proc.errorStream), "engine-stderr").apply { isDaemon = true; start() }

        val reader = BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8))
        val port = awaitHandshakePort(reader)

        // Any further stdout the engine writes (println/logging) is drained
        // in the background so its pipe buffer never fills up and stalls it.
        Thread(StdoutDrainer(reader), "engine-stdout").apply { isDaemon = true; start() }

        Runtime.getRuntime().addShutdownHook(Thread { stop() })

        val socket = Socket("127.0.0.1", port)
        EngineTransport(socket)
    }

    fun stop() {
        process?.let { p ->
            if (p.isAlive) {
                p.destroy()
                if (!p.waitFor(2, TimeUnit.SECONDS)) {
                    p.destroyForcibly()
                }
            }
        }
        process = null
    }

    private fun awaitHandshakePort(reader: BufferedReader): Int {
        // Bounded wait: a hung/missing engine binary must not freeze app startup.
        // 20s, not a smaller number: the one-file PyInstaller build re-extracts
        // its payload (numpy/scipy/matplotlib/reportlab, ~85MB) on EVERY launch,
        // not just the first. Measured cold start on Phase 5 test hardware
        // (Linux x86-64, SSD) was 3.7-4.7s across repeated trials — 20s leaves
        // real headroom for a slower disk or CPU on actual end-user machines
        // rather than cutting it close to what one dev machine measured.
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            val line = reader.readLine() ?: break
            val matcher = readyMarker.matcher(line)
            if (matcher.matches()) {
                val json = matcher.group(1)
                val portMatch = Regex("\"port\"\\s*:\\s*(\\d+)").find(json)
                    ?: error("READY handshake missing 'port': $json")
                return portMatch.groupValues[1].toInt()
            }
        }
        error("Engine process did not emit TOLERANCE_ENGINE_READY within timeout")
    }

    private fun resolveProcessBuilder(): ProcessBuilder {
        val packagedExe = locatePackagedExecutable()
        return if (packagedExe != null) {
            ProcessBuilder(packagedExe.absolutePath)
        } else {
            // Dev fallback — requires `pip install -e engine/[dev]` in a local venv.
            val root = findProjectRoot()
            val engineDir = File(root, "engine").absoluteFile
            val venvPython = if (isWindows()) {
                File(engineDir, ".venv/Scripts/python.exe")
            } else {
                File(engineDir, ".venv/bin/python")
            }

            val pythonCmd = if (venvPython.exists()) venvPython.absolutePath else "python"

            ProcessBuilder(pythonCmd, "-m", "tolerance_engine")
                .directory(File(engineDir, "src").absoluteFile)
        }
    }

    private fun findProjectRoot(): File {
        var current = File(".").absoluteFile
        while (current.parentFile != null) {
            if (File(current, "settings.gradle.kts").exists()) {
                return current
            }
            current = current.parentFile
        }
        // Fallback to current dir if not found
        return File(".").absoluteFile
    }

    /**
     * Copies the platform-appropriate frozen exe out of the (read-only, inside
     * the app bundle) resources directory into the OS app-data directory on
     * first run, so it can be marked executable and safely overwritten on
     * update. Returns null if no packaged exe is bundled (dev environment).
     */
    private fun locatePackagedExecutable(): File? {
        val platformDir = platformResourceDir()
        val exeName = if (isWindows()) "tolerance-engine.exe" else "tolerance-engine"
        val resourcePath = "/engine-dist/$platformDir/$exeName"

        val resourceStream = javaClass.getResourceAsStream(resourcePath) ?: return null

        val appDataDir = File(userAppDataDir(), "tolerance-stackup-studio/engine")
        appDataDir.mkdirs()
        val extracted = File(appDataDir, exeName)

        resourceStream.use { input ->
            extracted.outputStream().use { output -> input.copyTo(output) }
        }
        if (!isWindows()) {
            extracted.setExecutable(true)
        }
        return extracted
    }

    private fun platformResourceDir(): String {
        val os = System.getProperty("os.name").lowercase(Locale.ROOT)
        val arch = System.getProperty("os.arch").lowercase(Locale.ROOT)
        val osPart = when {
            os.contains("win") -> "windows"
            os.contains("mac") -> "macos"
            else -> "linux"
        }
        val archPart = if (arch.contains("aarch64") || arch.contains("arm")) "arm64" else "x64"
        return "$osPart-$archPart"
    }

    private fun isWindows() = System.getProperty("os.name").lowercase(Locale.ROOT).contains("win")

    private fun userAppDataDir(): File = when {
        isWindows() -> File(System.getenv("APPDATA") ?: System.getProperty("user.home"))
        System.getProperty("os.name").lowercase(Locale.ROOT).contains("mac") ->
            File(System.getProperty("user.home"), "Library/Application Support")
        else -> File(System.getProperty("user.home"), ".local/share")
    }

    private class StderrLogger(private val stream: InputStream) : Runnable {
        override fun run() {
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).forEachLine { line ->
                println("[engine:stderr] $line") // swap for a real logger in Phase 3
            }
        }
    }

    private class StdoutDrainer(private val reader: BufferedReader) : Runnable {
        override fun run() {
            reader.forEachLine { line -> println("[engine:stdout] $line") }
        }
    }
}
