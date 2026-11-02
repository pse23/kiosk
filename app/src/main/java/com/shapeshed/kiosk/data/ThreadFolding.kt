package com.shapeshed.kiosk.data

/** A comment row as displayed: [hiddenReplies] > 0 means its subtree is collapsed. */
data class VisibleComment(
    val flat: FlatComment,
    val hiddenReplies: Int,
)

/**
 * The flattened thread with the replies under each [collapsed] comment removed. A comment's
 * replies are the rows that follow it with a greater depth, up to the next row at its depth or
 * shallower.
 */
fun visibleThread(comments: List<FlatComment>, collapsed: Set<Long>): List<VisibleComment> {
    val out = ArrayList<VisibleComment>(comments.size)
    var i = 0
    while (i < comments.size) {
        val flat = comments[i]
        var end = i + 1
        if (flat.comment.id in collapsed) {
            while (end < comments.size && comments[end].depth > flat.depth) end++
        }
        out += VisibleComment(flat, hiddenReplies = end - i - 1)
        i = end
    }
    return out
}

/** Index of the first top-level row after [fromIndex], or null when it is the last thread. */
fun nextTopLevelIndex(rows: List<VisibleComment>, fromIndex: Int): Int? {
    for (i in (fromIndex + 1) until rows.size) {
        if (rows[i].flat.depth == 0) return i
    }
    return null
}
