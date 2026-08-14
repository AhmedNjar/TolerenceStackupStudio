package com.openamr.tolerencestackupstudio.engine

import com.openamr.tolerencestackupstudio.engine.protocol.Envelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

/**
 * Owns the raw socket and speaks the wire format:
 *   [4-byte big-endian length][UTF-8 JSON bytes]
 * matching engine/src/tolerance_engine/server.py's read_message/write_message.
 *
 * One request in flight at a time (protected by [writeLock]) — this is a
 * single-client desktop app talking to a single engine process, so a simple
 * request/response mutex is sufficient for Phase 1. If Monte Carlo PROGRESS
 * streaming is added later, this will need to become a demux keyed on
 * requestId instead of a strict request-then-response mutex.
 */
class EngineTransport(private val socket: Socket) : AutoCloseable {

    private val json = Json { ignoreUnknownKeys = true }
    private val input = DataInputStream(socket.getInputStream())
    private val output = DataOutputStream(socket.getOutputStream())
    private val writeLock = Mutex()

    /**
     * The actual read/write calls below (DataOutputStream/DataInputStream on
     * a plain java.net.Socket) are BLOCKING JVM I/O, not coroutine-friendly
     * non-blocking I/O — calling them directly inside a suspend fun without
     * withContext(Dispatchers.IO) would block whatever dispatcher the caller
     * is running on. For a call made via rememberCoroutineScope() from
     * Compose, that's the UI thread, which would freeze the entire window
     * (no recomposition, no animation) for the duration of every engine
     * call — mild for a quick PING, but a real problem for a multi-second
     * Monte Carlo + PDF report generation call. withContext(Dispatchers.IO)
     * moves the actual blocking work off the calling dispatcher.
     */
    suspend fun request(envelope: Envelope): Envelope = withContext(Dispatchers.IO) {
        writeLock.withLock {
            val body = json.encodeToString(Envelope.serializer(), envelope).toByteArray(Charsets.UTF_8)
            output.writeInt(body.size) // DataOutputStream.writeInt is big-endian
            output.write(body)
            output.flush()

            val length = input.readInt() // DataInputStream.readInt is big-endian
            val responseBytes = ByteArray(length)
            input.readFully(responseBytes)
            json.decodeFromString(Envelope.serializer(), responseBytes.toString(Charsets.UTF_8))
        }
    }

    override fun close() {
        runCatching { socket.close() }
    }
}
