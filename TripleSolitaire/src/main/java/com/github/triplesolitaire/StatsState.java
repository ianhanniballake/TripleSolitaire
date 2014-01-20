package com.github.triplesolitaire;

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;
import android.util.SparseArray;

import com.github.triplesolitaire.provider.GameContract;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Represents the player's stats associated with all the games they have played
 */
public class StatsState {
    private static class GameStats {
        int duration = 0;
        boolean loss = true;
        int moves = 0;
        boolean synced = true;

        public GameStats(final boolean synced) {
            this.synced = synced;
        }

        public GameStats(final int duration, final int moves, final boolean synced) {
            loss = false;
            this.duration = duration;
            this.moves = moves;
            this.synced = synced;
        }

        public GameStats(final JSONObject jsonObject) throws JSONException {
            if (!jsonObject.has("loss")) {
                loss = false;
                duration = jsonObject.getInt("duration");
                moves = jsonObject.getInt("moves");
            }
        }

        public JSONObject toJSON() throws JSONException {
            final JSONObject json = new JSONObject();
            if (loss)
                json.put("loss", true);
            else {
                json.put("duration", duration);
                json.put("moves", moves);
            }
            return json;
        }
    }

    // serialization format version
    private static final String SERIAL_VERSION = "1.0";
    private final SparseArray<GameStats> gameStats = new SparseArray<GameStats>();

    /**
     * Constructs an empty StatsState (i.e., no games played)
     */
    public StatsState() {
    }

    /**
     * Constructs a StatsState object from serialized data.
     *
     * @param data Serialized data to parse
     */
    public StatsState(final byte[] data) {
        this(new String(data));
    }

    /**
     * Constructs a StatsState object from Cursor data.
     *
     * @param data Cursor data to parse
     */
    public StatsState(final Cursor data) {
        final int startTimeColumnIndex = data.getColumnIndex(GameContract.Games.COLUMN_NAME_START_TIME);
        final int durationColumnIndex = data.getColumnIndex(GameContract.Games.COLUMN_NAME_DURATION);
        final int movesColumnIndex = data.getColumnIndex(GameContract.Games.COLUMN_NAME_MOVES);
        final int syncedColumnIndex = data.getColumnIndex(GameContract.Games.COLUMN_NAME_SYNCED);
        while (data.moveToNext()) {
            final int startTime = data.getInt(startTimeColumnIndex);
            final boolean synced = data.getInt(syncedColumnIndex) != 0;
            if (data.isNull(durationColumnIndex))
                gameStats.put(startTime, new GameStats(synced));
            else {
                final int duration = data.getInt(durationColumnIndex);
                final int moves = data.getInt(movesColumnIndex);
                gameStats.put(startTime, new GameStats(duration, moves, synced));
            }
        }
    }

    /**
     * Constructs a StatsState object from a JSON string.
     *
     * @param json JSON data to parse
     */
    public StatsState(final String json) {
        if (json == null || json.trim().equals(""))
            return; // default progress
        try {
            final JSONObject obj = new JSONObject(json);
            final String format = obj.getString("version");
            if (!format.equals(SERIAL_VERSION))
                throw new RuntimeException("Unexpected stats format " + format);
            final JSONObject levels = obj.getJSONObject("games");
            final Iterator<?> iter = levels.keys();
            while (iter.hasNext()) {
                final String startTime = (String) iter.next();
                final GameStats gameStat = new GameStats(levels.getJSONObject(startTime));
                gameStats.put(Integer.valueOf(startTime), gameStat);
            }
        } catch (final JSONException e) {
            Log.e(StatsState.class.getSimpleName(), "Error parsing JSON", e);
            throw new RuntimeException("Save data has a syntax error: " + json, e);
        } catch (final NumberFormatException e) {
            Log.e(StatsState.class.getSimpleName(), "Invalid number while parsing JSON", e);
            throw new RuntimeException("Save data has an invalid number in it: " + json, e);
        }
    }

    /**
     * Returns a clone of this StatsState object.
     */
    @Override
    public StatsState clone() {
        final StatsState result = new StatsState();
        final int size = gameStats.size();
        for (int index = 0; index < size; index++) {
            final int key = gameStats.keyAt(index);
            result.gameStats.put(key, gameStats.get(key));
        }
        return result;
    }

    /**
     * Get the average game duration of games won. This should only be called when the user has won at least one game
     *
     * @return Average game duration in seconds
     */
    public double getAverageDuration() {
        double durationSum = 0;
        final int size = gameStats.size();
        for (int index = 0; index < size; index++)
            durationSum += gameStats.get(gameStats.keyAt(index)).duration;
        return durationSum / getGamesWon();
    }

    /**
     * Gets the average game moves of games won. This should only be called when the user has won at least one game
     *
     * @return Average game moves
     */
    public double getAverageMoves() {
        double moveSum = 0;
        final int size = gameStats.size();
        for (int index = 0; index < size; index++)
            moveSum += gameStats.get(gameStats.keyAt(index)).moves;
        return moveSum / getGamesWon();
    }

    /**
     * Gets the total number of games played
     *
     * @return The number of games played
     */
    public int getGamesPlayed() {
        return gameStats.size();
    }

    /**
     * Gets the total number of games won
     *
     * @return The number of games won
     */
    public int getGamesWon() {
        int gamesWon = 0;
        for (int index = 0; index < gameStats.size(); index++)
            if (!gameStats.valueAt(index).loss)
                gamesWon++;
        return gamesWon;
    }

    /**
     * Gets a list of ContentProviderOperations that save all of the current game stats to the local ContentProvider.
     * Note that this does not look for already existing records, but assumes the ContentProvider can ignore duplicates
     *
     * @return List of operations needed to save these stats to the local ContentProvider
     */
    public ArrayList<ContentProviderOperation> getLocalSaveOperations() {
        final ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();
        final int size = gameStats.size();
        for (int index = 0; index < size; index++) {
            final int startTime = gameStats.keyAt(index);
            final GameStats stats = gameStats.get(startTime);
            final ContentValues values = new ContentValues();
            values.put(GameContract.Games.COLUMN_NAME_START_TIME, startTime);
            if (!stats.loss) {
                values.put(GameContract.Games.COLUMN_NAME_DURATION, stats.duration);
                values.put(GameContract.Games.COLUMN_NAME_MOVES, stats.moves);
            }
            values.put(GameContract.Games.COLUMN_NAME_SYNCED, stats.synced);
            operations.add(ContentProviderOperation.newInsert(GameContract.Games.CONTENT_URI).withValues(values)
                    .build());
        }
        return operations;
    }

    /**
     * Get longest win streak (i.e., multiple games won in a row
     *
     * @return The maximum number of games won in a row
     */
    public int getLongestWinStreak() {
        int currentStreak = 0;
        int longestWinStreak = 0;
        final int size = gameStats.size();
        for (int index = 0; index < size; index++) {
            final GameStats stats = gameStats.get(gameStats.keyAt(index));
            if (stats.loss) {
                longestWinStreak = Math.max(longestWinStreak, currentStreak);
                currentStreak = 0;
            } else
                currentStreak++;
        }
        longestWinStreak = Math.max(longestWinStreak, currentStreak);
        return longestWinStreak;
    }

    /**
     * Gets the minimum number of moves in any won game
     *
     * @param onlyUnsycned If only unsynced games should be considered
     * @return The minimum number of moves used to win a game
     */
    public int getMinimumMoves(final boolean onlyUnsycned) {
        int minimumMoves = Integer.MAX_VALUE;
        final int size = gameStats.size();
        for (int index = 0; index < size; index++) {
            final GameStats stats = gameStats.get(gameStats.keyAt(index));
            if (stats.loss || (onlyUnsycned && stats.synced))
                continue;
            minimumMoves = Math.min(minimumMoves, stats.moves);
        }
        return minimumMoves;
    }

    /**
     * Gets the shortest time (duration) in any won game
     *
     * @param onlyUnsycned If only unsynced games should be considered
     * @return The shortest time (in seconds) in any won game
     */
    public int getShortestTime(final boolean onlyUnsycned) {
        int shortestDuration = Integer.MAX_VALUE;
        final int size = gameStats.size();
        for (int index = 0; index < size; index++) {
            final GameStats stats = gameStats.get(gameStats.keyAt(index));
            if (stats.loss || (onlyUnsycned && stats.synced))
                continue;
            shortestDuration = Math.min(shortestDuration, stats.duration);
        }
        return shortestDuration;
    }

    /**
     * Serializes this StatsState to an array of bytes.
     *
     * @return A byte array representing this StatsState
     */
    public byte[] toBytes() {
        return toString().getBytes();
    }

    /**
     * Serializes this StatsState to a JSON string.
     */
    @Override
    public String toString() {
        try {
            final JSONObject games = new JSONObject();
            final int size = gameStats.size();
            for (int index = 0; index < size; index++) {
                final int startTime = gameStats.keyAt(index);
                games.put(Integer.toString(startTime), gameStats.get(startTime).toJSON());
            }
            final JSONObject obj = new JSONObject();
            obj.put("version", SERIAL_VERSION);
            obj.put("games", games);
            return obj.toString();
        } catch (final JSONException e) {
            Log.e(StatsState.class.getSimpleName(), "Error converting save data to JSON", e);
            throw new RuntimeException("Error converting save data to JSON.", e);
        }
    }

    /**
     * Computes the union of this StatsState with the given StatsState. The other stats always overwrite the current
     * stats
     *
     * @param other The other operand with which to compute the union.
     * @return The result of the union.
     */
    public StatsState unionWith(final StatsState other) {
        final StatsState result = clone();
        final int size = other.gameStats.size();
        for (int index = 0; index < size; index++) {
            final int key = other.gameStats.keyAt(index);
            // Other overwrites local stats. In almost all cases, they'll be the same as key conflicts will be near
            // impossible
            result.gameStats.put(key, other.gameStats.get(key));
        }
        return result;
    }
}
