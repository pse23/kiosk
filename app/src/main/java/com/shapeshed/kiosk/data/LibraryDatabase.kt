package com.shapeshed.kiosk.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The user's own library: saved stories, saved comments, and recently viewed stories. Kept in its
 * own database, apart from the reader cache, because that cache evicts and this must not.
 *
 * Rows snapshot enough of the item (title, url, text) to render offline without refetching.
 */
@Entity(tableName = "saved_stories")
data class SavedStoryEntity(
    @PrimaryKey val storyId: Long,
    val title: String,
    val url: String?,
    val by: String,
    val time: Long,
    val savedAtMillis: Long,
)

@Entity(tableName = "saved_comments")
data class SavedCommentEntity(
    @PrimaryKey val commentId: Long,
    val storyId: Long,
    val storyTitle: String,
    val by: String,
    val time: Long,
    val text: String,
    val savedAtMillis: Long,
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val storyId: Long,
    val title: String,
    val url: String?,
    val by: String,
    val time: Long,
    val viewedAtMillis: Long,
    /** Highest comment count seen on a visit; drives the "N new" badge in the feed. */
    @ColumnInfo(defaultValue = "0") val commentCount: Int = 0,
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM saved_stories ORDER BY savedAtMillis DESC")
    fun savedStories(): Flow<List<SavedStoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSavedStory(entity: SavedStoryEntity)

    @Query("DELETE FROM saved_stories WHERE storyId = :storyId")
    suspend fun deleteSavedStory(storyId: Long)

    @Query("SELECT * FROM saved_stories WHERE storyId = :storyId LIMIT 1")
    suspend fun savedStory(storyId: Long): SavedStoryEntity?

    @Query("SELECT storyId FROM saved_stories")
    suspend fun savedStoryIdList(): List<Long>

    @Query("SELECT * FROM history WHERE storyId = :storyId LIMIT 1")
    suspend fun historyEntry(storyId: Long): HistoryEntity?

    @Query("SELECT storyId, commentCount FROM history")
    fun seenCommentCounts(): Flow<List<SeenCommentCount>>

    @Query("SELECT * FROM saved_comments ORDER BY savedAtMillis DESC")
    fun savedComments(): Flow<List<SavedCommentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSavedComment(entity: SavedCommentEntity)

    @Query("DELETE FROM saved_comments WHERE commentId = :commentId")
    suspend fun deleteSavedComment(commentId: Long)

    @Query("SELECT * FROM saved_stories")
    suspend fun allSavedStories(): List<SavedStoryEntity>

    @Query("SELECT * FROM saved_comments")
    suspend fun allSavedComments(): List<SavedCommentEntity>

    @Query("SELECT * FROM history")
    suspend fun allHistory(): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSavedStories(entities: List<SavedStoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSavedComments(entities: List<SavedCommentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putHistoryEntries(entities: List<HistoryEntity>)

    @Query("SELECT * FROM history ORDER BY viewedAtMillis DESC")
    fun history(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putHistory(entity: HistoryEntity)

    @Query(
        "DELETE FROM history WHERE storyId NOT IN " +
            "(SELECT storyId FROM history ORDER BY viewedAtMillis DESC LIMIT :keep)",
    )
    suspend fun trimHistory(keep: Int)

    @Query("DELETE FROM history")
    suspend fun clearHistory()
}

@Database(
    entities = [SavedStoryEntity::class, SavedCommentEntity::class, HistoryEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history ADD COLUMN commentCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): LibraryDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                LibraryDatabase::class.java,
                "library.db",
            ).addMigrations(MIGRATION_1_2).build()
    }
}

data class SeenCommentCount(val storyId: Long, val commentCount: Int)

/** A story row as the library lists show it, independent of which table it came from. */
data class LibraryStory(
    val id: Long,
    val title: String,
    val url: String?,
    val by: String,
    val time: Long,
    /** When it was saved or last viewed, depending on the list. */
    val atMillis: Long,
)

/** Saved stories, saved comments, and reading history. Room runs suspend DAO calls off the main thread. */
class LibraryStore(private val dao: LibraryDao) {

    val savedStories: Flow<List<LibraryStory>> = dao.savedStories().map { rows ->
        rows.map { LibraryStory(it.storyId, it.title, it.url, it.by, it.time, it.savedAtMillis) }
    }

    val savedStoryIds: Flow<Set<Long>> = dao.savedStories().map { rows -> rows.mapTo(HashSet()) { it.storyId } }

    val savedComments: Flow<List<SavedCommentEntity>> = dao.savedComments()

    val savedCommentIds: Flow<Set<Long>> = dao.savedComments().map { rows -> rows.mapTo(HashSet()) { it.commentId } }

    /** Comment count per previously opened story, for the feed's "N new" badge. */
    val seenCommentCounts: Flow<Map<Long, Int>> = dao.seenCommentCounts().map { rows ->
        rows.associate { it.storyId to it.commentCount }
    }

    val history: Flow<List<LibraryStory>> = dao.history().map { rows ->
        rows.map { LibraryStory(it.storyId, it.title, it.url, it.by, it.time, it.viewedAtMillis) }
    }

    suspend fun setStorySaved(story: Story, saved: Boolean) {
        if (saved) {
            dao.putSavedStory(
                SavedStoryEntity(
                    storyId = story.id,
                    title = story.title,
                    url = story.url,
                    by = story.by,
                    time = story.time,
                    savedAtMillis = System.currentTimeMillis(),
                ),
            )
        } else {
            dao.deleteSavedStory(story.id)
        }
    }

    suspend fun unsaveStory(storyId: Long) = dao.deleteSavedStory(storyId)

    /** Ids the reader cache must keep: saved stories stay readable offline. */
    suspend fun pinnedStoryIds(): Set<Long> = dao.savedStoryIdList().toSet()

    /**
     * The library's own copy of a story, for opening it with no network. Saved stories win over
     * history. Score, comment ids and self-post text are not stored, so they come back empty.
     */
    suspend fun offlineStory(storyId: Long): Story? {
        dao.savedStory(storyId)?.let { return offlineStory(it.storyId, it.title, it.url, it.by, it.time) }
        dao.historyEntry(storyId)?.let { return offlineStory(it.storyId, it.title, it.url, it.by, it.time) }
        return null
    }

    private fun offlineStory(id: Long, title: String, url: String?, by: String, time: Long) = Story(
        id = id,
        title = title,
        url = url,
        by = by,
        score = 0,
        descendants = 0,
        time = time,
        kids = emptyList(),
        text = null,
    )

    suspend fun setCommentSaved(comment: Comment, storyId: Long, storyTitle: String, saved: Boolean) {
        if (saved) {
            dao.putSavedComment(
                SavedCommentEntity(
                    commentId = comment.id,
                    storyId = storyId,
                    storyTitle = storyTitle,
                    by = comment.by,
                    time = comment.time,
                    text = comment.text,
                    savedAtMillis = System.currentTimeMillis(),
                ),
            )
        } else {
            dao.deleteSavedComment(comment.id)
        }
    }

    suspend fun unsaveComment(commentId: Long) = dao.deleteSavedComment(commentId)

    /** Move [story] to the top of history, keeping the newest [MAX_HISTORY] entries. */
    suspend fun recordView(story: Story) {
        // Keep the highest count seen: the offline copy of a story reports 0 comments, and that
        // must not reset the baseline.
        val seen = maxOf(dao.historyEntry(story.id)?.commentCount ?: 0, story.descendants)
        dao.putHistory(
            HistoryEntity(
                storyId = story.id,
                title = story.title,
                url = story.url,
                by = story.by,
                time = story.time,
                viewedAtMillis = System.currentTimeMillis(),
                commentCount = seen,
            ),
        )
        dao.trimHistory(MAX_HISTORY)
    }

    suspend fun clearHistory() = dao.clearHistory()

    suspend fun exportBackup(): LibraryBackup =
        LibraryBackup(dao.allSavedStories(), dao.allSavedComments(), dao.allHistory())

    /** Merges [backup] in; an entry already present is replaced by the file's copy. */
    suspend fun importBackup(backup: LibraryBackup) {
        dao.putSavedStories(backup.savedStories)
        dao.putSavedComments(backup.savedComments)
        dao.putHistoryEntries(backup.history)
        dao.trimHistory(MAX_HISTORY)
    }

    private companion object {
        const val MAX_HISTORY = 200
    }
}
