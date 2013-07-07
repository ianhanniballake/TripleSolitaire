package com.github.triplesolitaire;

import java.util.ArrayList;
import java.util.Iterator;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.database.Cursor;
import android.util.SparseArray;

import com.github.triplesolitaire.provider.GameContract;

/**
 * Represents the player's progress in the game. The player's progress is how many stars they got on each level.
 */
public class StatsState
{
	public static class GameStats
	{
		int duration = 0;
		boolean loss = true;
		int moves = 0;

		public GameStats()
		{
		}

		public GameStats(final int duration, final int moves)
		{
			loss = false;
			this.duration = duration;
			this.moves = moves;
		}

		public GameStats(final JSONObject jsonObject) throws JSONException
		{
			if (!jsonObject.has("loss"))
			{
				loss = false;
				duration = jsonObject.getInt("duration");
				moves = jsonObject.getInt("moves");
			}
		}

		public JSONObject toJSON() throws JSONException
		{
			final JSONObject json = new JSONObject();
			if (loss)
				json.put("loss", true);
			else
			{
				json.put("duration", duration);
				json.put("moves", moves);
			}
			return json;
		}
	}

	// serialization format version
	private static final String SERIAL_VERSION = "1.0";
	SparseArray<GameStats> gameStats = new SparseArray<GameStats>();

	/** Constructs an empty SaveState object. No stars on no levels. */
	public StatsState()
	{
	}

	/** Constructs a SaveState object from serialized data. */
	public StatsState(final byte[] data)
	{
		if (data == null)
			return; // default progress
		loadFromJson(new String(data));
	}

	public StatsState(final Cursor data)
	{
		final int startTimeColumnIndex = data.getColumnIndex(GameContract.Games.COLUMN_NAME_START_TIME);
		final int durationColumnIndex = data.getColumnIndex(GameContract.Games.COLUMN_NAME_DURATION);
		final int movesColumnIndex = data.getColumnIndex(GameContract.Games.COLUMN_NAME_MOVES);
		while (data.moveToNext())
		{
			final int startTime = data.getInt(startTimeColumnIndex);
			if (data.isNull(durationColumnIndex))
				gameStats.put(startTime, new GameStats());
			else
			{
				final int duration = data.getInt(durationColumnIndex);
				final int moves = data.getInt(movesColumnIndex);
				gameStats.put(startTime, new GameStats(duration, moves));
			}
		}
	}

	/** Constructs a SaveState object from a JSON string. */
	public StatsState(final String json)
	{
		if (json == null)
			return; // default progress
		loadFromJson(json);
	}

	/** Returns a clone of this SaveState object. */
	@Override
	public StatsState clone()
	{
		final StatsState result = new StatsState();
		final int size = gameStats.size();
		for (int index = 0; index < size; index++)
		{
			final int key = gameStats.keyAt(index);
			result.gameStats.put(key, gameStats.get(key));
		}
		return result;
	}

	public double getAverageDuration()
	{
		double durationSum = 0;
		final int size = gameStats.size();
		for (int index = 0; index < size; index++)
			durationSum += gameStats.get(gameStats.keyAt(index)).duration;
		return durationSum / getGamesWon();
	}

	public double getAverageMoves()
	{
		double moveSum = 0;
		final int size = gameStats.size();
		for (int index = 0; index < size; index++)
			moveSum += gameStats.get(gameStats.keyAt(index)).moves;
		return moveSum / getGamesWon();
	}

	public int getGamesPlayed()
	{
		return gameStats.size();
	}

	public int getGamesWon()
	{
		int gamesWon = 0;
		for (int index = 0; index < gameStats.size(); index++)
			if (!gameStats.valueAt(index).loss)
				gamesWon++;
		return gamesWon;
	}

	public ArrayList<ContentProviderOperation> getLocalSaveOperations()
	{
		final ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();
		final int size = gameStats.size();
		for (int index = 0; index < size; index++)
		{
			final int startTime = gameStats.keyAt(index);
			final GameStats stats = gameStats.get(startTime);
			final ContentValues values = new ContentValues();
			values.put(GameContract.Games.COLUMN_NAME_START_TIME, startTime);
			if (!stats.loss)
			{
				values.put(GameContract.Games.COLUMN_NAME_DURATION, stats.duration);
				values.put(GameContract.Games.COLUMN_NAME_MOVES, stats.moves);
			}
			operations.add(ContentProviderOperation.newInsert(GameContract.Games.CONTENT_URI).withValues(values)
					.build());
		}
		return operations;
	}

	/** Replaces this SaveState's content with the content loaded from the given JSON string. */
	public void loadFromJson(final String json)
	{
		gameStats.clear();
		if (json == null || json.trim().equals(""))
			return;
		try
		{
			final JSONObject obj = new JSONObject(json);
			final String format = obj.getString("version");
			if (!format.equals(SERIAL_VERSION))
				throw new RuntimeException("Unexpected stats format " + format);
			final JSONObject levels = obj.getJSONObject("games");
			final Iterator<?> iter = levels.keys();
			while (iter.hasNext())
			{
				final String startTime = (String) iter.next();
				final GameStats gameStat = new GameStats(levels.getJSONObject(startTime));
				gameStats.put(Integer.valueOf(startTime), gameStat);
			}
		} catch (final JSONException ex)
		{
			ex.printStackTrace();
			throw new RuntimeException("Save data has a syntax error: " + json, ex);
		} catch (final NumberFormatException ex)
		{
			ex.printStackTrace();
			throw new RuntimeException("Save data has an invalid number in it: " + json, ex);
		}
	}

	/** Serializes this SaveState to an array of bytes. */
	public byte[] toBytes()
	{
		return toString().getBytes();
	}

	/** Serializes this SaveState to a JSON string. */
	@Override
	public String toString()
	{
		try
		{
			final JSONObject games = new JSONObject();
			final int size = gameStats.size();
			for (int index = 0; index < size; index++)
			{
				final int startTime = gameStats.keyAt(index);
				games.put(Integer.toString(startTime), gameStats.get(startTime).toJSON());
			}
			final JSONObject obj = new JSONObject();
			obj.put("version", SERIAL_VERSION);
			obj.put("games", games);
			return obj.toString();
		} catch (final JSONException ex)
		{
			ex.printStackTrace();
			throw new RuntimeException("Error converting save data to JSON.", ex);
		}
	}

	/**
	 * Computes the union of this SaveState with the given SaveState. The union will have any levels present in either
	 * operand. If the same level is present in both operands, then the number of stars will be the greatest of the two.
	 * 
	 * @param other
	 *            The other operand with which to compute the union.
	 * @return The result of the union.
	 */
	public StatsState unionWith(final StatsState other)
	{
		final StatsState result = clone();
		final int size = other.gameStats.size();
		for (int index = 0; index < size; index++)
		{
			final int key = other.gameStats.keyAt(index);
			// Other overwrites local stats. In almost all cases, they'll be the same as key conflicts will be near
			// impossible
			result.gameStats.put(key, other.gameStats.get(key));
		}
		return result;
	}
}
