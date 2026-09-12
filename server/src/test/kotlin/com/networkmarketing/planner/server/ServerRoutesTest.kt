package com.networkmarketing.planner.server

import com.networkmarketing.planner.domain.compensation.PayoutBreakdown
import com.networkmarketing.planner.domain.model.PlannerState
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ServerRoutesTest {

    private val dataFile = File.createTempFile("planner-test", ".json").also { it.delete() }

    @After
    fun cleanup() {
        dataFile.delete()
    }

    private fun runServerTest(block: suspend (io.ktor.client.HttpClient) -> Unit) = testApplication {
        application { plannerModule(StateStore(dataFile)) }
        // The default test client is enough; responses are decoded from text manually.
        block(client)
    }

    @Test
    fun `seeds sample organization on first read`() = runServerTest { client ->
        val state: PlannerState = client.get("/api/state").let {
            assertEquals(HttpStatusCode.OK, it.status)
            Json { ignoreUnknownKeys = true }.decodeFromString(PlannerState.serializer(), it.bodyAsText())
        }
        assertTrue("sample org should have members", state.snapshot.members.isNotEmpty())
        assertTrue("sample org should have nodes", state.snapshot.nodes.isNotEmpty())
    }

    @Test
    fun `adding volume increases the estimated payout`() = runServerTest { client ->
        val json = Json { ignoreUnknownKeys = true }
        val before: PayoutBreakdown? = json.decodeFromString(
            CalculatorResponse.serializer(),
            client.get("/api/calculator?kind=CURRENT").bodyAsText(),
        ).root
        val youId = "n-you-current"

        val add = client.post("/api/nodes") {
            contentType(ContentType.Application.Json)
            setBody("""{"kind":"CURRENT","parentId":"$youId","name":"Test Leg","personalPv":1500}""")
        }
        assertEquals(HttpStatusCode.Created, add.status)

        val after: PayoutBreakdown? = json.decodeFromString(
            CalculatorResponse.serializer(),
            client.get("/api/calculator?kind=CURRENT").bodyAsText(),
        ).root

        assertTrue("root payout should be present", before != null && after != null)
        assertTrue(
            "estimate should rise after adding 1500 PV (before=${before!!.estimatedMonthly}, after=${after!!.estimatedMonthly})",
            after.estimatedMonthly > before.estimatedMonthly,
        )
    }

    @Test
    fun `moving You under another node is rejected`() = runServerTest { client ->
        val response = client.post("/api/nodes/n-you-current/reparent") {
            contentType(ContentType.Application.Json)
            setBody("""{"parentId":"n-alex"}""")
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
    }
}
