package com.example.phoneagent.data.llm

import com.example.phoneagent.data.llm.sse.RawSseEvent
import com.example.phoneagent.data.llm.sse.SseParser
import com.example.phoneagent.data.llm.sse.toModelEvent
import com.example.phoneagent.domain.model.ModelEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseSseSmokeTest {
    @Test
    fun `release variant parses a serialized SSE model response`() {
        val parser = SseParser()
        val response =
            parser.feed(
                "event: response.output_text.delta\ndata: {\"delta\":\"Hello from model\"}\n\n" +
                    "event: response.completed\ndata: {\"response\":{\"usage\":null}}\n\n",
            )

        assertEquals(
            listOf(
                RawSseEvent("response.output_text.delta", "{\"delta\":\"Hello from model\"}"),
                RawSseEvent("response.completed", "{\"response\":{\"usage\":null}}"),
            ),
            response,
        )
        assertEquals(
            listOf(ModelEvent.TextDelta("Hello from model"), ModelEvent.Completed()),
            response.mapNotNull { it.toModelEvent() },
        )
    }
}
