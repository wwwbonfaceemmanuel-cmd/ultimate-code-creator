package com.ultimate.codecreator.engine

import com.ultimate.codecreator.model.CloudEndpoint
import com.ultimate.codecreator.model.CloudGenerationException
import com.ultimate.codecreator.model.GenerationSource
import com.ultimate.codecreator.model.SupportedLanguage
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class CloudCodeGeneratorTest {
    private lateinit var server: MockWebServer
    private lateinit var generator: OpenAiCloudGenerator

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
        generator = OpenAiCloudGenerator(client)
    }

    @After fun tearDown() { server.shutdown() }

    private fun endpoint() = CloudEndpoint(server.url("/v1").toString().trimEnd('/'), "test-model", "sk-test")

    private fun completion(content: String): String = JSONObject()
        .put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", content))))
        .toString()

    @Test fun extractsFencedCodeAndLabelsSource() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(completion("```python\nprint('hi')\n```")))
        val result = generator.generate("say hi", SupportedLanguage.PYTHON, endpoint())
        assertEquals("print('hi')", result.code)
        assertEquals(GenerationSource.CLOUD_AI, result.source)
        assertEquals("test-model", result.model)
    }

    @Test fun plainContentIsUsedWhenNoFence() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(completion("print('hi')")))
        val result = generator.generate("say hi", SupportedLanguage.PYTHON, endpoint())
        assertEquals("print('hi')", result.code)
    }

    @Test fun requestIncludesAuthModelAndPrompt() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(completion("print('hi')")))
        generator.generate("build a widget", SupportedLanguage.PYTHON, endpoint())
        val recorded = server.takeRequest()
        assertEquals("Bearer sk-test", recorded.getHeader("Authorization"))
        assertTrue(recorded.path!!.endsWith("/chat/completions"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"model\":\"test-model\""))
        assertTrue(body.contains("build a widget"))
        assertTrue(body.contains("messages"))
    }

    @Test fun authFailureThrows() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        val error = assertThrows(CloudGenerationException::class.java) {
            runBlocking { generator.generate("hi", SupportedLanguage.PYTHON, endpoint()) }
        }
        assertTrue(error.message!!.contains("Authentication"))
    }

    @Test fun serverErrorThrows() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        val error = assertThrows(CloudGenerationException::class.java) {
            runBlocking { generator.generate("hi", SupportedLanguage.PYTHON, endpoint()) }
        }
        assertTrue(error.message!!.contains("server error"))
    }

    @Test fun malformedResponseThrows() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val error = assertThrows(CloudGenerationException::class.java) {
            runBlocking { generator.generate("hi", SupportedLanguage.PYTHON, endpoint()) }
        }
        assertTrue(error.message!!.contains("unexpected"))
    }

    @Test fun emptyCodeThrows() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(completion("   ")))
        val error = assertThrows(CloudGenerationException::class.java) {
            runBlocking { generator.generate("hi", SupportedLanguage.PYTHON, endpoint()) }
        }
        assertTrue(error.message!!.contains("no code"))
    }

    @Test fun extractCodeHandlesFencedAndPlain() {
        assertEquals("print(1)", OpenAiCloudGenerator.extractCode("```python\nprint(1)\n```"))
        assertEquals("print(1)", OpenAiCloudGenerator.extractCode("Here you go:\n```\nprint(1)\n```\nHope that helps"))
        assertEquals("print(1)", OpenAiCloudGenerator.extractCode("  print(1)  "))
    }
}
