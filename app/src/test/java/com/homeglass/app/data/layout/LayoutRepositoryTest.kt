package com.homeglass.app.data.layout

import com.homeglass.app.domain.layout.LayoutIssue
import com.homeglass.app.domain.layout.LayoutState
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutRepositoryTest {

    private class FakeCache(var document: String? = null) : LayoutCache {
        var writes = 0
        override suspend fun read(): String? = document
        override suspend fun write(raw: String) {
            document = raw
            writes++
        }

        override suspend fun clear() {
            document = null
        }
    }

    private class FakeSource(private val document: String?) : LayoutSource {
        override suspend fun load(): String? = document
    }

    private class FailingSource : LayoutSource {
        override suspend fun load(): String? = throw IOException("add-on injoignable")
    }

    @Test
    fun `local layout is exposed and cached`() = runTest {
        val cache = FakeCache()
        val repository = LayoutRepository(cache = cache, local = FakeSource(VALID))

        repository.refresh()

        val state = repository.state.value
        assertTrue("état inattendu : $state", state is LayoutState.Ready)
        assertEquals(12, (state as LayoutState.Ready).layout.revision)
        assertEquals(VALID, cache.document)
        assertEquals(1, cache.writes)
    }

    @Test
    fun `corrupted cache does not prevent a fresh load`() = runTest {
        val cache = FakeCache(document = "{ pas du json")
        val repository = LayoutRepository(cache = cache, local = FakeSource(VALID))

        repository.refresh()

        assertTrue(repository.state.value is LayoutState.Ready)
        assertEquals(VALID, cache.document)
    }

    @Test
    fun `newer layout keeps the cached document and asks for an update`() = runTest {
        val cache = FakeCache(document = VALID)
        val repository = LayoutRepository(cache = cache, local = FakeSource(NEWER))

        repository.refresh()

        val state = repository.state.value
        assertTrue("état inattendu : $state", state is LayoutState.Stale)
        state as LayoutState.Stale
        assertEquals(LayoutIssue.UpdateApp, state.issue)
        assertEquals(12, state.layout.revision)
        // Le cache n'est pas écrasé par une version illisible pour cette app.
        assertEquals(VALID, cache.document)
    }

    @Test
    fun `newer layout without cache is an error`() = runTest {
        val repository = LayoutRepository(cache = FakeCache(), local = FakeSource(NEWER))

        repository.refresh()

        assertEquals(LayoutState.Error(LayoutIssue.UpdateApp), repository.state.value)
    }

    @Test
    fun `corrupted fresh layout keeps the cache`() = runTest {
        val repository = LayoutRepository(cache = FakeCache(document = VALID), local = FakeSource("{ oops"))

        repository.refresh()

        val state = repository.state.value
        assertTrue(state is LayoutState.Stale)
        assertEquals(LayoutIssue.CorruptedLayout, (state as LayoutState.Stale).issue)
        assertEquals(12, state.layout.revision)
    }

    @Test
    fun `corrupted fresh layout without cache is an error`() = runTest {
        val repository = LayoutRepository(cache = FakeCache(), local = FakeSource("{ oops"))

        repository.refresh()

        assertEquals(LayoutState.Error(LayoutIssue.CorruptedLayout), repository.state.value)
    }

    @Test
    fun `no source at all reports missing data`() = runTest {
        val repository = LayoutRepository(cache = FakeCache(), local = FakeSource(null))

        repository.refresh()

        assertEquals(LayoutState.Error(LayoutIssue.NoData), repository.state.value)
    }

    @Test
    fun `cached layout survives an unreachable remote source`() = runTest {
        val repository = LayoutRepository(
            cache = FakeCache(document = VALID),
            local = FakeSource(null),
            remote = FailingSource(),
        )

        repository.refresh()

        val state = repository.state.value
        assertTrue("état inattendu : $state", state is LayoutState.Stale)
        state as LayoutState.Stale
        assertEquals(LayoutIssue.Offline, state.issue)
        assertEquals(12, state.layout.revision)
    }

    @Test
    fun `remote layout wins over the local asset`() = runTest {
        val cache = FakeCache()
        val repository = LayoutRepository(
            cache = cache,
            local = FakeSource(VALID),
            remote = FakeSource(NEWER_REVISION),
        )

        repository.refresh()

        val state = repository.state.value
        assertTrue(state is LayoutState.Ready)
        assertEquals(77, (state as LayoutState.Ready).layout.revision)
        assertEquals(NEWER_REVISION, cache.document)
    }

    private companion object {
        val VALID = """
            {
              "schemaVersion": 1,
              "revision": 12,
              "home": { "title": "Maison", "sections": [] }
            }
        """.trimIndent()

        val NEWER_REVISION = """
            {
              "schemaVersion": 1,
              "revision": 77,
              "home": { "title": "Maison", "sections": [] }
            }
        """.trimIndent()

        val NEWER = """
            {
              "schemaVersion": 2,
              "revision": 99,
              "home": { "title": "Maison du futur", "sections": [] }
            }
        """.trimIndent()
    }
}
