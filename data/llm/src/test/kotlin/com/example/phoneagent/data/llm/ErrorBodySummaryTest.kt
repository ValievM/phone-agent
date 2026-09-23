package com.example.phoneagent.data.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ErrorBodySummaryTest {
    @Test
    fun `an openai style error keeps only its identifiers and never the message`() {
        val body =
            """{"error":{"message":"Invalid 'input[0].content[0].text': 'SECRET chat text'.","type":"invalid_request_error",""" +
                """"param":"input[0].content[0].text","code":"invalid_value"}}"""

        val summary = summarizeErrorBody(body)

        assertEquals("type=invalid_request_error code=invalid_value param=input[0].content[0].text", summary)
        assertFalse(summary.contains("SECRET"))
    }

    @Test
    fun `a flat detail message is reported by its field name only`() {
        val summary = summarizeErrorBody("""{"detail":"SECRET file name.pdf is not allowed"}""")

        assertEquals("fields=detail", summary)
        assertFalse(summary.contains("SECRET"))
    }

    @Test
    fun `an error given as a plain code is kept`() {
        assertEquals("error=rate_limit_exceeded", summarizeErrorBody("""{"error":"rate_limit_exceeded"}"""))
    }

    @Test
    fun `an identifier that looks like free text is dropped`() {
        val summary = summarizeErrorBody("""{"error":{"type":"invalid_request_error","code":"my secret sentence here"}}""")

        assertEquals("type=invalid_request_error", summary)
    }

    @Test
    fun `an over-long identifier is dropped`() {
        val summary = summarizeErrorBody("""{"error":{"type":"${"a".repeat(65)}","code":"ok_code"}}""")

        assertEquals("code=ok_code", summary)
    }

    @Test
    fun `a body that is not json is reported by size only`() {
        val summary = summarizeErrorBody("<html>SECRET gateway page</html>")

        assertEquals("non-JSON body of 32 chars", summary)
    }

    @Test
    fun `an empty body is said to be empty`() {
        assertEquals("empty body", summarizeErrorBody(""))
    }

    @Test
    fun `json without any error identifiers lists nothing but its shape`() {
        assertEquals("no error identifiers", summarizeErrorBody("""{"error":{"message":"SECRET"}}"""))
    }
}
