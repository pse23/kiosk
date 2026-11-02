package com.shapeshed.kiosk.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThreadFoldingTest {

    private fun c(id: Long, depth: Int) = FlatComment(
        Comment(id = id, by = "u$id", time = 0, text = "", kids = emptyList(), deleted = false, dead = false),
        depth,
    )

    // 1
    //   2
    //     3
    //   4
    // 5
    //   6
    private val thread = listOf(c(1, 0), c(2, 1), c(3, 2), c(4, 1), c(5, 0), c(6, 1))

    @Test
    fun nothingCollapsedShowsEveryRow() {
        val rows = visibleThread(thread, emptySet())
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), rows.map { it.flat.comment.id })
        assertEquals(List(6) { 0 }, rows.map { it.hiddenReplies })
    }

    @Test
    fun collapsingATopLevelCommentHidesItsWholeSubtree() {
        val rows = visibleThread(thread, setOf(1L))
        assertEquals(listOf(1L, 5L, 6L), rows.map { it.flat.comment.id })
        assertEquals(3, rows.first().hiddenReplies)
    }

    @Test
    fun collapsingAReplyHidesOnlyItsDescendants() {
        val rows = visibleThread(thread, setOf(2L))
        assertEquals(listOf(1L, 2L, 4L, 5L, 6L), rows.map { it.flat.comment.id })
        assertEquals(1, rows[1].hiddenReplies)
    }

    @Test
    fun collapsingALeafHidesNothing() {
        val rows = visibleThread(thread, setOf(3L))
        assertEquals(6, rows.size)
        assertEquals(0, rows[2].hiddenReplies)
    }

    @Test
    fun nextTopLevelSkipsReplies() {
        val rows = visibleThread(thread, emptySet())
        assertEquals(4, nextTopLevelIndex(rows, 0))
        assertEquals(4, nextTopLevelIndex(rows, 2))
        assertNull(nextTopLevelIndex(rows, 4))
    }
}
