package com.networkmarketing.planner.server

import com.networkmarketing.planner.domain.compensation.CompensationEngine
import com.networkmarketing.planner.domain.compensation.GapAnalyzer
import com.networkmarketing.planner.domain.model.PlannerSettings
import com.networkmarketing.planner.domain.model.PlannerState
import com.networkmarketing.planner.domain.model.StructureKind
import com.networkmarketing.planner.domain.model.UserGoals
import com.networkmarketing.planner.domain.ops.SnapshotOps
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import java.io.File

private const val DEFAULT_PORT = 8080

fun main() {
    val port = System.getenv("PLANNER_PORT")?.toIntOrNull() ?: DEFAULT_PORT
    val dataFile = File(System.getenv("PLANNER_DATA_FILE") ?: "planner-data.json")
    val store = StateStore(dataFile)
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        plannerModule(store)
    }.start(wait = true)
}

fun Application.plannerModule(store: StateStore) {
    val engine = CompensationEngine()
    val gapAnalyzer = GapAnalyzer(engine)

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }
    install(CallLogging)
    install(CORS) {
        anyHost()
        allowHeader(io.ktor.http.HttpHeaders.ContentType)
        allowMethod(io.ktor.http.HttpMethod.Put)
        allowMethod(io.ktor.http.HttpMethod.Post)
        allowMethod(io.ktor.http.HttpMethod.Delete)
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(cause.message ?: "Server error"))
        }
    }

    routing {
        plannerApi(store, engine, gapAnalyzer)
        // Serve the browser app (index.html + assets) from resources/web.
        staticResources("/", "web", index = "index.html")
    }
}

private fun io.ktor.server.routing.Routing.plannerApi(
    store: StateStore,
    engine: CompensationEngine,
    gapAnalyzer: GapAnalyzer,
) {
    get("/api/health") { call.respond(mapOf("status" to "ok")) }

    get("/api/state") { call.respond(store.read()) }

    put("/api/state") {
        val incoming = call.receive<PlannerState>()
        call.respond(store.update { incoming })
    }

    put("/api/goals") {
        val goals = call.receive<UserGoals>()
        call.respond(store.update { it.copy(goals = goals) })
    }

    put("/api/settings") {
        val settings = call.receive<PlannerSettings>()
        call.respond(store.update { it.copy(settings = settings) })
    }

    post("/api/nodes") {
        val body = call.receive<AddNodeRequest>()
        var newId = ""
        val state = store.update { current ->
            val (snapshot, id) = SnapshotOps.addNode(
                snapshot = current.snapshot,
                kind = body.kind,
                parentId = body.parentId,
                name = body.name,
                personalPv = body.personalPv,
                bvPerPv = current.settings.bvPerPv,
                partnerName = body.partnerName,
                isCouple = body.isCouple,
            )
            newId = id
            current.copy(snapshot = snapshot)
        }
        call.respond(HttpStatusCode.Created, AddNodeResponse(newId, state))
    }

    put("/api/nodes/{id}") {
        val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
        val body = call.receive<UpdateNodeRequest>()
        val state = store.update { current ->
            val bv = body.personalPv * current.settings.bvPerPv
            var snapshot = SnapshotOps.updatePerson(
                snapshot = current.snapshot,
                nodeId = id,
                name = body.name,
                partnerName = body.partnerName,
                isCouple = body.isCouple,
                notes = body.notes,
                personalPv = body.personalPv,
                personalBv = bv,
            )
            if (body.canvasX != null && body.canvasY != null) {
                snapshot = SnapshotOps.move(snapshot, id, body.canvasX, body.canvasY)
            }
            current.copy(snapshot = snapshot)
        }
        call.respond(state)
    }

    post("/api/nodes/{id}/move") {
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
        val body = call.receive<MoveRequest>()
        call.respond(store.update { it.copy(snapshot = SnapshotOps.move(it.snapshot, id, body.x, body.y)) })
    }

    post("/api/nodes/{id}/reparent") {
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
        val body = call.receive<ReparentRequest>()
        val current = store.read()
        val updated = SnapshotOps.setParent(current.snapshot, id, body.parentId)
            ?: return@post call.respond(HttpStatusCode.Conflict, ErrorResponse("Invalid line of sponsorship (cycle, cross-structure, or moving You)"))
        call.respond(store.update { it.copy(snapshot = updated) })
    }

    delete("/api/nodes/{id}") {
        val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
        call.respond(store.update { it.copy(snapshot = SnapshotOps.deleteSubtree(it.snapshot, id)) })
    }

    post("/api/layout") {
        val kind = call.kindParam()
        call.respond(store.update { it.copy(snapshot = SnapshotOps.applyLayout(it.snapshot, kind)) })
    }

    post("/api/sample-data") {
        call.respond(store.update { it.copy(snapshot = SnapshotOps.restoreSample(it.settings.bvPerPv)) })
    }

    post("/api/copy-current-to-ideal") {
        call.respond(store.update { it.copy(snapshot = SnapshotOps.copyCurrentToIdeal(it.snapshot, it.settings.bvPerPv)) })
    }

    get("/api/calculator") {
        val kind = call.kindParam()
        val state = store.read()
        val root = engine.evaluateRoot(state.snapshot, kind, state.settings)
        val perNode = state.snapshot.nodes(kind).associate { it.id to engine.evaluateNode(state.snapshot, it.id, state.settings) }
        call.respond(CalculatorResponse(kind = kind, root = root, perNode = perNode))
    }

    get("/api/gap") {
        val state = store.read()
        val gap = gapAnalyzer.compare(state.snapshot, state.settings, state.goals)
            ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("No current structure to analyze"))
        call.respond(gap)
    }

    get("/api/config") { call.respond(engine.config()) }
}

private fun io.ktor.server.application.ApplicationCall.kindParam(): StructureKind {
    val raw = request.queryParameters["kind"]?.uppercase()
    return if (raw == StructureKind.IDEAL.name) StructureKind.IDEAL else StructureKind.CURRENT
}
