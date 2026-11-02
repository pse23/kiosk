package com.shapeshed.kiosk.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
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
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM saved_stories ORDER BY savedAtMillis DESC")
    fun savedStories(): Flow<List<SavedStoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSavedStory(entity: SavedStoryEntity)

    @Query("DELETE FROM saved_stories WHERE storyId = :storyId")
    suspend fun deleteSavedStory(storyId: Long)

    @Query("SELECT * FROM saved_comments ORDER BY savedAtMillis DESC")
    fun savedComments(): Flow<List<SavedCommentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSavedComment(entity: SavedCommentEntity)

    @Query("DELETE FROM saved_comments WHERE commentId = :commentId")
    suspend fun deleteSavedComment(commentId: Long)

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
    version = 1,
    exportSchema = true,
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        fun create(context: Context): LibraryDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                LibraryDatabase::class.java,
                "library.db",
            ).build()
    }
}

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
        dao.putHistory(
            HistoryEntity(
                storyId = story.id,
                title = story.title,
                url = story.url,
                by = story.by,
                time = story.time,
                viewedAtMillis = System.currentTimeMillis(),
            ),
        )
        dao.trimHistory(MAX_HISTORY)
    }

    suspend fun clearHistory() = dao.clearHistory()

    private companion object {
        const val MAX_HISTORY = 200
    }
}
