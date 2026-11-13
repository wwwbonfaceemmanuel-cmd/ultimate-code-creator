package com.ultimate.codecreator

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ultimate.codecreator.data.KeystoreSecretStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecretStoreTest {
    @Test fun encryptsDecryptsAndClears() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = KeystoreSecretStore(context)
        val key = "test_api_key"
        try {
            store.put(key, "sk-secret-12345")
            assertEquals("sk-secret-12345", store.get(key))
            // Overwrite with a new value.
            store.put(key, "sk-rotated-67890")
            assertEquals("sk-rotated-67890", store.get(key))
        } finally {
            store.clear(key)
        }
        assertNull(store.get(key))
    }
}
