package com.shapeshed.kiosk.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryBackupTest {

    private val backup = LibraryBackup(
        savedStories = listOf(SavedStoryEntity(1, "A story", "https://example.com/a", "alice", 100, 1_000)),
        savedComments = listOf(SavedCommentEntity(2, 1, "A story", "bob", 200, "a \"quoted\"\nline", 2_000)),
        history = listOf(
            HistoryEntity(1, "A story", "https://example.com/a", "alice", 100, 3_000, commentCount = 42),
            HistoryEntity(3, "Ask HN: text post", null, "carol", 300, 4_000),
        ),
    )

    @Test
    fun roundTripKeepsEveryField() {
        assertEquals(backup, decodeLibraryBackup(encodeLibraryBackup(backup)))
    }

    @Test
    fun nullUrlSurvivesTheRoundTrip() {
        assertNull(decodeLibraryBackup(encodeLibraryBackup(backup)).history[1].url)
    }

    @Test
    fun missingListsReadAsEmpty() {
        val decoded = decodeLibraryBackup("""{"format":"kiosk-library","version":1}""")
        assertEquals(0, decoded.size)
    }

    @Test
    fun entriesMissingRequiredFieldsAreSkipped() {
        val decoded = decodeLibraryBackup(
            """{"format":"kiosk-library","version":1,"savedStories":[{"title":"no id"},{"id":5,"title":"ok"}]}""",
        )
        assertEquals(listOf(5L), decoded.savedStories.map { it.storyId })
    }

    @Test
    fun otherFilesAreRejected() {
        assertTrue(runCatching { decodeLibraryBackup("""{"hello":"world"}""") }.isFailure)
        assertTrue(runCatching { decodeLibraryBackup("not json") }.isFailure)
        assertTrue(runCatching { decodeLibraryBackup("""{"format":"kiosk-library","version":99}""") }.isFailure)
    }
}
