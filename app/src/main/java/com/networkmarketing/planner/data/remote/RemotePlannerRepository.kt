package com.networkmarketing.planner.data.remote

import android.util.Log
import com.networkmarketing.planner.data.repository.PlannerStore
import com.networkmarketing.planner.domain.model.OrgNode
import com.networkmarketing.planner.domain.model.OrgSnapshot
import com.networkmarketing.planner.domain.model.PlannerSettings
import com.networkmarketing.planner.domain.model.PlannerState
import com.networkmarketing.planner.domain.model.StructureKind
import com.networkmarketing.planner.domain.model.UserGoals
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * [PlannerStore] backed by the shared server. The server owns the data; every read and
 * every edit goes through the REST API, and the in-memory [state] mirrors the latest
 * server response so the reactive UI updates exactly like it did with Room.
 */
class RemotePlannerRepository(
    baseUrl: String,
) : PlannerStore {

    private val base = baseUrl.trimEnd('/')
    private val state = MutableStateFlow(PlannerState())

    private val client = HttpClient(OkHttp) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
    }

    override val snapshot: Flow<OrgSnapshot> = state.map { it.snapshot }
    override val prefs: Flow<Pair<UserGoals, PlannerSettings>> = state.map { it.goals to it.settings }

    override suspend fun ensureSeeded() {
        runCatching { client.get("$base/api/state").body<PlannerState>() }
            .onSuccess { state.value = it }
            .onFailure { Log.e(TAG, "Failed to load state from $base", it) }
    }

    override suspend fun restoreSampleData() = post("$base/api/sample-data")

    override suspend fun saveGoals(goals: UserGoals) = put("$base/api/goals", goals)

    override suspend fun saveSettings(settings: PlannerSettings) = put("$base/api/settings", settings)

    override suspend fun addNode(
        kind: StructureKind,
        canvasX: Float,
        canvasY: Float,
        parentId: String?,
        name: String,
        personalPv: Double,
        bvPerPv: Double,
        partnerName: String,
        isCouple: Boolean,
    ): String {
        return runCatching {
            val resp: AddNodeResp = client.post("$base/api/nodes") {
                contentType(ContentType.Application.Json)
                setBody(AddNodeReq(kind, parentId, name, personalPv, partnerName, isCouple))
            }.body()
            state.value = resp.state
            // Honor the caller's requested canvas position.
            moveInternal(resp.nodeId, canvasX, canvasY)
            resp.nodeId
        }.onFailure { Log.e(TAG, "addNode failed", it) }.getOrDefault("")
    }

    override suspend fun savePerson(
        node: OrgNode,
        name: String,
        partnerName: String,
        isCouple: Boolean,
        notes: String,
        personalPv: Double,
        personalBv: Double,
    ) {
        put("$base/api/nodes/${node.id}", UpdateNodeReq(name, partnerName, isCouple, notes, personalPv))
    }

    override suspend fun updatePosition(node: OrgNode, canvasX: Float, canvasY: Float) {
        moveInternal(node.id, canvasX, canvasY)
    }

    override suspend fun setParent(snapshot: OrgSnapshot, childId: String, parentId: String?): Boolean {
        // The server returns 409 for an invalid line of sponsorship; expectSuccess turns that
        // into an exception, so a failure here simply means "not allowed".
        return runCatching {
            val newState: PlannerState = client.post("$base/api/nodes/$childId/reparent") {
                contentType(ContentType.Application.Json)
                setBody(ReparentReq(parentId))
            }.body()
            state.value = newState
            true
        }.getOrDefault(false)
    }

    override suspend fun applyLayout(snapshot: OrgSnapshot, kind: StructureKind) =
        post("$base/api/layout?kind=${kind.name}")

    override suspend fun deleteSubtree(snapshot: OrgSnapshot, nodeId: String) {
        runCatching { state.value = client.delete("$base/api/nodes/$nodeId").body() }
            .onFailure { Log.e(TAG, "delete failed", it) }
    }

    override suspend fun copyCurrentToIdeal(snapshot: OrgSnapshot, bvPerPv: Double) =
        post("$base/api/copy-current-to-ideal")

    private suspend fun moveInternal(nodeId: String, x: Float, y: Float) {
        runCatching {
            state.value = client.post("$base/api/nodes/$nodeId/move") {
                contentType(ContentType.Application.Json)
                setBody(MoveReq(x, y))
            }.body()
        }.onFailure { Log.e(TAG, "move failed", it) }
    }

    private suspend inline fun <reified T> put(url: String, body: T) {
        runCatching {
            state.value = client.put(url) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }.body()
        }.onFailure { Log.e(TAG, "PUT $url failed", it) }
    }

    private suspend fun post(url: String) {
        runCatching { state.value = client.post(url).body() }
            .onFailure { Log.e(TAG, "POST $url failed", it) }
    }

    companion object {
        private const val TAG = "RemotePlanner"
    }
}

@Serializable
private data class AddNodeReq(
    val kind: StructureKind,
    val parentId: String?,
    val name: String,
    val personalPv: Double,
    val partnerName: String,
    val isCouple: Boolean,
)

@Serializable
private data class UpdateNodeReq(
    val name: String,
    val partnerName: String,
    val isCouple: Boolean,
    val notes: String,
    val personalPv: Double,
)

@Serializable
private data class MoveReq(val x: Float, val y: Float)

@Serializable
private data class ReparentReq(val parentId: String?)

@Serializable
private data class AddNodeResp(val nodeId: String, val state: PlannerState)
