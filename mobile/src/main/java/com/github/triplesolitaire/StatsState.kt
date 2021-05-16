package com.github.triplesolitaire

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.database.Cursor
import android.text.format.DateUtils
import android.util.Log
import android.util.SparseArray
import androidx.core.util.forEach
import androidx.core.util.plus
import androidx.core.util.set
import androidx.core.util.valueIterator
import com.github.triplesolitaire.provider.GameContract
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/**
 * Represents the player's stats associated with all the games they have played
 */
class StatsState private constructor(
    private val gameStats: SparseArray<GameStats> = SparseArray()
) {
    private data class GameStats(
        val synced: Boolean = true,
        val duration: Int = 0,
        val moves: Int = 0,
        val loss: Boolean = false,
    ) {
        @Throws(JSONException::class)
        fun toJSON(): JSONObject {
            val json = JSONObject()
            if (loss) {
                json.put("loss", true)
            } else {
                json.put("duration", duration)
                json.put("moves", moves)
            }
            return json
        }
    }

    private fun GameStats(
        jsonObject: JSONObject
    ) = if (!jsonObject.has("loss") && jsonObject.getInt("duration") != 0) {
        GameStats(
            duration = jsonObject.getInt("duration"),
            moves = jsonObject.getInt("moves")
        )
    } else {
        GameStats()
    }

    /**
     * Constructs a StatsState object from serialized data.
     *
     * @param data Serialized data to parse
     */
    constructor(data: ByteArray) : this(String(data))

    /**
     * Constructs a StatsState object from Cursor data.
     *
     * @param data Cursor data to parse
     */
    constructor(data: Cursor?) : this() {
        if (data == null) {
            return
        }
        val startTimeColumnIndex = data.getColumnIndex(GameContract.Games.COLUMN_NAME_START_TIME)
        val durationColumnIndex = data.getColumnIndex(GameContract.Games.COLUMN_NAME_DURATION)
        val movesColumnIndex = data.getColumnIndex(GameContract.Games.COLUMN_NAME_MOVES)
        val syncedColumnIndex = data.getColumnIndex(GameContract.Games.COLUMN_NAME_SYNCED)
        while (data.moveToNext()) {
            val startTime = data.getInt(startTimeColumnIndex)
            val synced = data.getInt(syncedColumnIndex) != 0
            if (data.isNull(durationColumnIndex) || data.getInt(durationColumnIndex) == 0) {
                gameStats[startTime] = GameStats(synced)
            } else {
                val duration = data.getInt(durationColumnIndex)
                val moves = data.getInt(movesColumnIndex)
                gameStats[startTime] = GameStats(synced, duration, moves)
            }
        }
    }

    /**
     * Constructs a StatsState object from a JSON string.
     *
     * @param json JSON data to parse
     */
    constructor(json: String?) : this() {
        if (json.isNullOrBlank()) return  // default progress
        try {
            val obj = JSONObject(json)
            val format = obj.getString("version")
            if (format != SERIAL_VERSION) throw RuntimeException("Unexpected stats format $format")
            val levels = obj.getJSONObject("games")
            levels.keys().forEach { startTime ->
                val gameStat = GameStats(levels.getJSONObject(startTime))
                gameStats[startTime.toInt()] = gameStat
            }
        } catch (e: JSONException) {
            Log.e(StatsState::class.java.simpleName, "Error parsing JSON", e)
            throw RuntimeException("Save data has a syntax error: $json", e)
        } catch (e: NumberFormatException) {
            Log.e(StatsState::class.java.simpleName, "Invalid number while parsing JSON", e)
            throw RuntimeException("Save data has an invalid number in it: $json", e)
        }
    }

    /**
     * Get the average game duration of games won. This should only be called when the
     * user has won at least one game
     *
     * @return Average game duration in seconds
     */
    val averageDuration: Double
        get() {
            val durationSum = gameStats.valueIterator().asSequence().map {
                it.duration.toDouble()
            }.sum()
            return durationSum / getGamesWon(false)
        }

    /**
     * Gets the average game moves of games won. This should only be called when the
     * user has won at least one game
     *
     * @return Average game moves
     */
    val averageMoves: Double
        get() {
            val moveSum = gameStats.valueIterator().asSequence().map {
                it.moves.toDouble()
            }.sum()
            return moveSum / getGamesWon(false)
        }

    /**
     * Gets the total number of games played
     *
     * @return The number of games played
     */
    val gamesPlayed: Int
        get() = gameStats.size()

    /**
     * Gets the total number of games won
     *
     * @param onlyUnsynced If only unsynced games should be considered
     * @return The number of games won
     */
    fun getGamesWon(
        onlyUnsynced: Boolean
    ) = gameStats.valueIterator().asSequence().filterNot { stats ->
        stats.loss
    }.filterNot { stats ->
        onlyUnsynced && stats.synced
    }.count()

    /**
     * Gets the number of unsynced games
     * @return The number of unsynced games
     */
    val gamesUnsynced: Int
        get() {
            return gameStats.valueIterator().asSequence().count { stats ->
                !stats.synced
            }
        }

    /**
     * Gets the total time played across all games
     *
     * @return The time played across all games in milliseconds
     */
    val totalPlayedTimeMillis: Long
        get() {
            return gameStats.valueIterator().asSequence().map { it.duration }.sumOf { duration ->
                duration * DateUtils.SECOND_IN_MILLIS
            }
        }

    /**
     * Gets a list of ContentProviderOperations that save all of the current game stats
     * to the local ContentProvider.
     *
     * Note that this does not look for already existing records, but assumes the
     * ContentProvider can ignore duplicates
     *
     * @return List of operations needed to save these stats to the local ContentProvider
     */
    val localSaveOperations: ArrayList<ContentProviderOperation>
        get() {
            val operations = ArrayList<ContentProviderOperation>()
            gameStats.forEach { startTime, stats ->
                val values = ContentValues()
                values.put(GameContract.Games.COLUMN_NAME_START_TIME, startTime)
                if (stats.loss) {
                    values.putNull(GameContract.Games.COLUMN_NAME_DURATION)
                    values.putNull(GameContract.Games.COLUMN_NAME_MOVES)
                } else {
                    values.put(GameContract.Games.COLUMN_NAME_DURATION, stats.duration)
                    values.put(GameContract.Games.COLUMN_NAME_MOVES, stats.moves)
                }
                values.put(GameContract.Games.COLUMN_NAME_SYNCED, stats.synced)
                operations.add(
                    ContentProviderOperation.newInsert(GameContract.Games.CONTENT_URI)
                        .withValues(values)
                        .build()
                )
            }
            return operations
        }

    /**
     * Get longest win streak (i.e., multiple games won in a row
     *
     * @return The maximum number of games won in a row
     */
    val longestWinStreak: Int
        get() {
            return gameStats.valueIterator().asSequence().fold(0 to 0) {
                    (currentStreak, longestWinStreak), stats ->
                if (stats.loss) {
                    0 to max(currentStreak, longestWinStreak)
                } else {
                    currentStreak + 1 to max(currentStreak + 1, longestWinStreak)
                }
            }.second
        }

    /**
     * Gets the minimum number of moves in any won game
     *
     * @param onlyUnsynced If only unsynced games should be considered
     * @return The minimum number of moves used to win a game
     */
    fun getMinimumMoves(onlyUnsynced: Boolean): Int {
        var numConsidered = 0
        val minimumMoves = gameStats.valueIterator().asSequence().filterNot { stats ->
            stats.loss || onlyUnsynced && stats.synced
        }.fold(Int.MAX_VALUE) { minimum, stats ->
            numConsidered++
            min(minimum, stats.moves)
        }
        if (BuildConfig.DEBUG) {
            Log.d(
                StatsState::class.java.simpleName,
                "Get Minimum Moves" + (if (onlyUnsynced) " (Unsynced)" else "")
                        + ": considered " + numConsidered
            )
        }
        return minimumMoves
    }

    /**
     * Gets the shortest time (duration) in any won game
     *
     * @param onlyUnsynced If only unsynced games should be considered
     * @return The shortest time (in seconds) in any won game
     */
    fun getShortestTime(onlyUnsynced: Boolean): Int {
        var numConsidered = 0
        val shortestDuration = gameStats.valueIterator().asSequence().filterNot { stats ->
            stats.loss || onlyUnsynced && stats.synced
        }.fold(Int.MAX_VALUE) { minimum, stats ->
            numConsidered++
            min(minimum, stats.duration)
        }
        if (BuildConfig.DEBUG) {
            Log.d(
                StatsState::class.java.simpleName,
                "Get Shorted Time" + (if (onlyUnsynced) " (Unsynced)" else "")
                        + ": considered " + numConsidered
            )
        }
        return shortestDuration
    }

    /**
     * Serializes this StatsState to an array of bytes.
     *
     * @return A byte array representing this StatsState
     */
    fun toBytes(): ByteArray {
        return toString().toByteArray()
    }

    /**
     * Serializes this StatsState to a JSON string.
     */
    override fun toString(): String {
        return try {
            val games = JSONObject()
            gameStats.forEach { startTime, stats ->
                games.put(startTime.toString(), stats.toJSON())
            }
            JSONObject().apply {
                put("version", SERIAL_VERSION)
                put("games", games)
            }.toString()
        } catch (e: JSONException) {
            Log.e(StatsState::class.java.simpleName, "Error converting save data to JSON", e)
            throw RuntimeException("Error converting save data to JSON.", e)
        }
    }

    /**
     * Computes the union of this StatsState with the given StatsState.
     * The other stats always overwrite the current stats
     *
     * @param other The other operand with which to compute the union.
     * @return The result of the union.
     */
    fun unionWith(other: StatsState) = StatsState(gameStats + other.gameStats)

    companion object {
        // serialization format version
        private const val SERIAL_VERSION = "1.0"
    }
}
