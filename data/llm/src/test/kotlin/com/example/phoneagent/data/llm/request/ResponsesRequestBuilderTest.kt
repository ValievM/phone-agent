package com.example.phoneagent.data.llm.request

import com.example.phoneagent.data.llm.BackendProfile
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.model.ModelRequest
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.ToolSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsesRequestBuilderTest {
    @Test
    fun `store is false and stream is true`() {
        val body = buildResponsesRequestBody(request(), profile(), "session-1")

        assertEquals(false, body.getValue("store").jsonPrimitive.boolean)
        assertEquals(true, body.getValue("stream").jsonPrimitive.boolean)
    }

    @Test
    fun `instructions is not empty`() {
        val body = buildResponsesRequestBody(request(), profile(instructions = "be nice"), "session-1")

        assertEquals("be nice", body.getValue("instructions").jsonPrimitive.content)
    }

    @Test
    fun `the model and session id are carried from the profile and caller`() {
        val body = buildResponsesRequestBody(request(), profile(model = "gpt-x"), "session-42")

        assertEquals("gpt-x", body.getValue("model").jsonPrimitive.content)
        assertEquals("session-42", body.getValue("prompt_cache_key").jsonPrimitive.content)
    }

    @Test
    fun `a user message becomes an input_text message item`() {
        val body =
            buildResponsesRequestBody(
                request(history = listOf(HistoryItem.UserMessage("1", "hi"))),
                profile(),
                "session-1",
            )

        val item =
            body
                .getValue("input")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("message", item.getValue("type").jsonPrimitive.content)
        assertEquals("user", item.getValue("role").jsonPrimitive.content)
        val content =
            item
                .getValue("content")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("input_text", content.getValue("type").jsonPrimitive.content)
        assertEquals("hi", content.getValue("text").jsonPrimitive.content)
    }

    @Test
    fun `an assistant message becomes an output_text message item`() {
        val body =
            buildResponsesRequestBody(
                request(history = listOf(HistoryItem.AssistantMessage("1", "hello there"))),
                profile(),
                "session-1",
            )

        val item =
            body
                .getValue("input")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("message", item.getValue("type").jsonPrimitive.content)
        assertEquals("assistant", item.getValue("role").jsonPrimitive.content)
        val content =
            item
                .getValue("content")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("output_text", content.getValue("type").jsonPrimitive.content)
        assertEquals("hello there", content.getValue("text").jsonPrimitive.content)
    }

    @Test
    fun `a tool call item is echoed with its id, call_id, name and arguments`() {
        val arguments = Json.parseToJsonElement("""{"city":"Москва"}""").jsonObject
        val body =
            buildResponsesRequestBody(
                request(history = listOf(HistoryItem.ToolCallItem("item-1", "call-1", "get_local_time", arguments))),
                profile(),
                "session-1",
            )

        val item =
            body
                .getValue("input")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("function_call", item.getValue("type").jsonPrimitive.content)
        assertEquals("item-1", item.getValue("id").jsonPrimitive.content)
        assertEquals("call-1", item.getValue("call_id").jsonPrimitive.content)
        assertEquals("get_local_time", item.getValue("name").jsonPrimitive.content)
        assertEquals(arguments, Json.parseToJsonElement(item.getValue("arguments").jsonPrimitive.content))
    }

    @Test
    fun `a tool output becomes function_call_output using the same call_id`() {
        val body =
            buildResponsesRequestBody(
                request(history = listOf(HistoryItem.ToolOutput("item-1", "call-1", "14:32"))),
                profile(),
                "session-1",
            )

        val item =
            body
                .getValue("input")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("function_call_output", item.getValue("type").jsonPrimitive.content)
        assertEquals("call-1", item.getValue("call_id").jsonPrimitive.content)
        assertEquals("14:32", item.getValue("output").jsonPrimitive.content)
    }

    @Test
    fun `tools are translated to the function tool schema the backend accepts`() {
        val schema = Json.parseToJsonElement("""{"type":"object","properties":{}}""").jsonObject
        val tool = ToolSpec(name = "get_local_time", description = "current time", inputSchema = schema, risk = Risk.SAFE)
        val body = buildResponsesRequestBody(request(tools = listOf(tool)), profile(), "session-1")

        val toolJson =
            body
                .getValue("tools")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("function", toolJson.getValue("type").jsonPrimitive.content)
        assertEquals("get_local_time", toolJson.getValue("name").jsonPrimitive.content)
        assertEquals("current time", toolJson.getValue("description").jsonPrimitive.content)
        assertEquals(schema, toolJson.getValue("parameters").jsonObject)
        assertTrue(body.getValue("tool_choice").jsonPrimitive.content == "auto")
    }

    private fun request(
        history: List<HistoryItem> = emptyList(),
        tools: List<ToolSpec> = emptyList(),
    ): ModelRequest = ModelRequest(history, tools)

    private fun profile(
        model: String = "gpt-test",
        instructions: String = "instructions",
    ): BackendProfile = BackendProfile(baseUrl = "https://example.test", model = model, instructions = instructions)
}
