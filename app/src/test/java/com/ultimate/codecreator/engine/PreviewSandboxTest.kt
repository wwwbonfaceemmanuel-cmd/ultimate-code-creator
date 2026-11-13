package com.ultimate.codecreator.engine

import org.junit.Assert.*
import org.junit.Test

class PreviewSandboxTest {
    @Test
    fun policyBlocksConnectionsAndExternalResources() {
        val wrapped = PreviewSandbox.wrap("<html><head><title>Test</title></head><body></body></html>")
        assertTrue(wrapped.contains("connect-src 'none'"))
        assertTrue(wrapped.contains("default-src 'none'"))
        assertTrue(wrapped.contains("form-action 'none'"))
        assertEquals(1, Regex("Content-Security-Policy").findAll(wrapped).count())
        assertTrue(wrapped.indexOf("Content-Security-Policy") < wrapped.indexOf("<title>"))
    }

    @Test
    fun fragmentsAndDoctypeDocumentsBothReceivePolicy() {
        assertTrue(PreviewSandbox.wrap("<div>Fragment</div>").startsWith("<meta http-equiv="))
        val document = PreviewSandbox.wrap("<!DOCTYPE html><html><body>Test</body></html>")
        assertTrue(document.indexOf("<!DOCTYPE html>") < document.indexOf("Content-Security-Policy"))
    }
}
