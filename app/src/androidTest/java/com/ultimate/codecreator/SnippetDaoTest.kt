package com.ultimate.codecreator

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ultimate.codecreator.data.local.AppDatabase
import com.ultimate.codecreator.data.local.SnippetEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnippetDaoTest {
    @Test fun insertSearchAndDelete() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = database.snippetDao()
            dao.upsert(SnippetEntity("1", "Hello", "PYTHON", "print('hello')", "demo", false, 1L, 1L))
            assertEquals(1, dao.observeAll().first().size)
            assertEquals("Hello", dao.observeSearch("hello").first().single().title)
            dao.delete("1")
            assertEquals(0, dao.count())
        } finally {
            database.close()
        }
    }
}
