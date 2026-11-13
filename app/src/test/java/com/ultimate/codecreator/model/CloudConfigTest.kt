package com.ultimate.codecreator.model

import org.junit.Assert.*
import org.junit.Test

class CloudConfigTest {
    @Test fun httpsUrlIsValid() {
        assertTrue(CloudConfig.isValidBaseUrl("https://api.openai.com/v1"))
        assertTrue(CloudConfig.isValidBaseUrl("https://openrouter.ai/api/v1"))
    }

    @Test fun httpAndBlankAndMalformedAreRejected() {
        assertFalse(CloudConfig.isValidBaseUrl("http://api.openai.com/v1"))
        assertFalse(CloudConfig.isValidBaseUrl(""))
        assertFalse(CloudConfig.isValidBaseUrl("https://"))
        assertFalse(CloudConfig.isValidBaseUrl("not a url"))
    }

    @Test fun normalizeStripsTrailingSlashAndWhitespace() {
        assertEquals("https://api.test/v1", CloudConfig.normalizeBaseUrl("  https://api.test/v1/  "))
    }

    @Test fun isConfiguredRequiresKeyUrlAndModel() {
        assertFalse(CloudConfig(baseUrl = "https://api.test/v1", model = "m", hasApiKey = false).isConfigured)
        assertFalse(CloudConfig(baseUrl = "", model = "m", hasApiKey = true).isConfigured)
        assertFalse(CloudConfig(baseUrl = "https://api.test/v1", model = "", hasApiKey = true).isConfigured)
        assertTrue(CloudConfig(baseUrl = "https://api.test/v1", model = "m", hasApiKey = true).isConfigured)
    }
}
