package com.shapeshed.kiosk.data

import org.json.JSONArray
import org.json.JSONObject

/** Everything in the library, as written to and read from a backup file. */
data class LibraryBackup(
    val savedStories: List<SavedStoryEntity>,
    val savedComments: List<SavedCommentEntity>,
    val history: List<HistoryEntity>,
) {
    val size: Int get() = savedStories.size + savedComments.size + history.size
}

private const val BACKUP_FORMAT = "kiosk-library"
private const val BACKUP_VERSION = 1

fun encodeLibraryBackup(backup: LibraryBackup): String = JSONObject()
    .put("format", BACKUP_FORMAT)
    .put("version", BACKUP_VERSION)
    .put("savedStories", JSONArray(backup.savedStories.map { s ->
        JSONObject()
            .put("id", s.storyId).put("title", s.title).put("url", s.url ?: JSONObject.NULL)
            .put("by", s.by).put("time", s.time).put("savedAt", s.savedAtMillis)
    }))
    .put("savedComments", JSONArray(backup.savedComments.map { c ->
        JSONObject()
            .put("id", c.commentId).put("storyId", c.storyId).put("storyTitle", c.storyTitle)
            .put("by", c.by).put("time", c.time).put("text", c.text).put("savedAt", c.savedAtMillis)
    }))
    .put("history", JSONArray(backup.history.map { h ->
        JSONObject()
            .put("id", h.storyId).put("title", h.title).put("url", h.url ?: JSONObject.NULL)
            .put("by", h.by).put("time", h.time).put("viewedAt", h.viewedAtMillis)
            .put("commentCount", h.commentCount)
    }))
    .toString(2)

/**
 * Reads a backup written by [encodeLibraryBackup]. Missing lists read as empty; entries missing a
 * required field are skipped. Throws [IllegalArgumentException] for a file that isn't a Kiosk
 * library backup or comes from a newer format version.
 */
fun decodeLibraryBackup(json: String): LibraryBackup {
    val root = runCatching { JSONObject(json) }.getOrElse { throw IllegalArgumentException("not JSON", it) }
    require(root.optString("format") == BACKUP_FORMAT) { "not a Kiosk library backup" }
    require(root.optInt("version", 0) in 1..BACKUP_VERSION) { "unsupported backup version" }
    return LibraryBackup(
        savedStories = root.objects("savedStories").mapNotNull { o ->
            if (!o.has("id") || !o.has("title")) return@mapNotNull null
            SavedStoryEntity(
                storyId = o.getLong("id"),
                title = o.getString("title"),
                url = o.optNullableString("url"),
                by = o.optString("by"),
                time = o.optLong("time"),
                savedAtMillis = o.optLong("savedAt"),
            )
        },
        savedComments = root.objects("savedComments").mapNotNull { o ->
            if (!o.has("id") || !o.has("storyId")) return@mapNotNull null
            SavedCommentEntity(
                commentId = o.getLong("id"),
                storyId = o.getLong("storyId"),
                storyTitle = o.optString("storyTitle"),
                by = o.optString("by"),
                time = o.optLong("time"),
                text = o.optString("text"),
                savedAtMillis = o.optLong("savedAt"),
            )
        },
        history = root.objects("history").mapNotNull { o ->
            if (!o.has("id") || !o.has("title")) return@mapNotNull null
            HistoryEntity(
                storyId = o.getLong("id"),
                title = o.getString("title"),
                url = o.optNullableString("url"),
                by = o.optString("by"),
                time = o.optLong("time"),
                viewedAtMillis = o.optLong("viewedAt"),
                commentCount = o.optInt("commentCount"),
            )
        },
    )
}

private fun JSONObject.objects(key: String): List<JSONObject> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}

private fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
