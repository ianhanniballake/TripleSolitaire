package com.github.triplesolitaire.provider;

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Defines a contract between the Game content provider and its clients. A contract defines the information that a
 * client needs to access the provider as one or more data tables. A contract is a public, non-extendable (final) class
 * that contains constants defining column names and URIs. A well-written client depends only on the constants in the
 * contract.
 */
public final class GameContract
{
	/**
	 * Contraction table contract
	 */
	public static final class Games implements BaseColumns
	{
		/**
		 * The table name offered by this provider
		 */
		public static final String TABLE_NAME = "games";
        /**
         * Column name for whether this game has been synced (for leaderboards)
         * <P>
         * Type: INTEGER (boolean)
         * </P>
         */
        public static final String COLUMN_NAME_SYNCED = "synced";
		/**
		 * Column name for the game's duration (in seconds) to completion
		 * <P>
		 * Type: INTEGER
		 * </P>
		 */
		public static final String COLUMN_NAME_DURATION = "duration";
		/**
		 * Column name of the number of moves to completion
		 * <P>
		 * Type: INTEGER
		 * </P>
		 */
		public static final String COLUMN_NAME_MOVES = "moves";
		/**
		 * Column name of the game's start time
		 * <P>
		 * Type: INTEGER (long representing milliseconds)
		 * </P>
		 */
		public static final String COLUMN_NAME_START_TIME = "start_time";
		/**
		 * The content URI base for a single game. Callers must append a numeric game id to this Uri to retrieve a game
		 */
		public static final Uri CONTENT_ID_URI_BASE = Uri.parse(SCHEME + AUTHORITY + "/" + TABLE_NAME + "/");
		/**
		 * The content URI match pattern for a single game, specified by its ID. Use this to match incoming URIs or to
		 * construct an Intent.
		 */
		public static final Uri CONTENT_ID_URI_PATTERN = Uri.parse(SCHEME + AUTHORITY + "/" + TABLE_NAME + "/#");
		/**
		 * The MIME type of a {@link #CONTENT_URI} sub-directory of a single game.
		 */
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.ianhanniballake.solitairegame";
		/**
		 * The MIME type of {@link #CONTENT_URI} providing a directory of games.
		 */
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.ianhanniballake.solitairegame";
		/**
		 * The content:// style URL for this table
		 */
		public static final Uri CONTENT_URI = Uri.parse(SCHEME + AUTHORITY + "/games");
		/**
		 * The default sort order for this table
		 */
		public static final String DEFAULT_SORT_ORDER = COLUMN_NAME_START_TIME + " DESC";

		/**
		 * This class cannot be instantiated
		 */
		private Games()
		{
		}
	}

	/**
	 * Base authority for this content provider
	 */
	public static final String AUTHORITY = "com.github.triplesolitaire";
	/**
	 * The scheme part for this provider's URI
	 */
	private static final String SCHEME = "content://";

	/**
	 * This class cannot be instantiated
	 */
	private GameContract()
	{
	}
}