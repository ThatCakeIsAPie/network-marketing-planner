package com.networkmarketing.planner.server

import com.networkmarketing.planner.domain.model.PlannerSettings
import com.networkmarketing.planner.domain.model.PlannerState
import com.networkmarketing.planner.domain.ops.SnapshotOps
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Thread-safe, file-backed store for the single shared [PlannerState]. This is the
 * source of truth that the web app, phone browser, and Android app all read and write.
 *
 * Persistence is a plain JSON file so the server has no external database dependency;
 * the format is a superset of what the clients exchange and can be swapped for a real
 * database later without changing the routes.
 */
class StateStore(private val file: File) {
    private val mutex = Mutex()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile
    private var cached: PlannerState = load()

    private fun load(): PlannerState {
        if (file.exists()) {
            runCatching { json.decodeFromString(PlannerState.serializer(), file.readText()) }
                .getOrNull()
                ?.let { return it }
        }
        return seed()
    }

    private fun seed(): PlannerState {
        val state = PlannerState(
            snapshot = SnapshotOps.restoreSample(PlannerSettings.DEFAULT_BV_PER_PV),
        )
        persist(state)
        return state
    }

    private fun persist(state: PlannerState) {
        file.parentFile?.mkdirs()
        // Write to a temp file and move it into place so a crash never leaves a half file.
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(PlannerState.serializer(), state))
        tmp.copyTo(file, overwrite = true)
        tmp.delete()
    }

    fun current(): PlannerState = cached

    suspend fun read(): PlannerState = mutex.withLock { cached }

    /** Apply an update atomically and persist the result. Returns the new state. */
    suspend fun update(transform: (PlannerState) -> PlannerState): PlannerState = mutex.withLock {
        val next = transform(cached)
        cached = next
        persist(next)
        next
    }
}
