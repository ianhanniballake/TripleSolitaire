package com.github.triplesolitaire.provider;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

import com.github.triplesolitaire.BuildConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;

/**
 * Provides access to a database of games.
 */
public class GameProvider extends ContentProvider {
    /**
     * This class helps open, create, and upgrade the database file.
     */
    static class DatabaseHelper extends SQLiteOpenHelper {
        /**
         * Creates a new DatabaseHelper
         *
         * @param context context of this database
         */
        DatabaseHelper(final Context context) {
            super(context, GameProvider.DATABASE_NAME, null, GameProvider.DATABASE_VERSION);
        }

        /**
         * Creates the underlying database with table name and column names taken from the GameContract class.
         */
        @Override
        public void onCreate(final SQLiteDatabase db) {
            if (BuildConfig.DEBUG)
                Log.d(GameProvider.TAG, "Creating the " + GameContract.Games.TABLE_NAME + " table");
            db.execSQL("CREATE TABLE " + GameContract.Games.TABLE_NAME + " (" + BaseColumns._ID
                    + " INTEGER PRIMARY KEY AUTOINCREMENT," + GameContract.Games.COLUMN_NAME_START_TIME + " INTEGER,"
                    + GameContract.Games.COLUMN_NAME_DURATION + " INTEGER," + GameContract.Games.COLUMN_NAME_MOVES
                    + " INTEGER," + GameContract.Games.COLUMN_NAME_SYNCED + " INTEGER, "
                    + "UNIQUE(" + GameContract.Games.COLUMN_NAME_START_TIME + ")" + ");");
        }

        /**
         * Demonstrates that the provider must consider what happens when the underlying database is changed. Note that
         * this currently just destroys and recreates the database - should upgrade in place
         */
        @Override
        public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
            Log.w(GameProvider.TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);
            if (oldVersion == 1) {
                String tempTableName = "temp";
                db.execSQL("ALTER TABLE " + GameContract.Games.TABLE_NAME + " RENAME TO " + tempTableName);
                // Adds SYNCED column and UNIQUE on START_TIME
                db.execSQL("CREATE TABLE " + GameContract.Games.TABLE_NAME + " (" + BaseColumns._ID
                        + " INTEGER PRIMARY KEY AUTOINCREMENT," + GameContract.Games.COLUMN_NAME_START_TIME
                        + " INTEGER," + GameContract.Games.COLUMN_NAME_DURATION + " INTEGER, "
                        + GameContract.Games.COLUMN_NAME_MOVES + " INTEGER," + GameContract.Games.COLUMN_NAME_SYNCED
                        + " INTEGER, " + "UNIQUE(" + GameContract.Games.COLUMN_NAME_START_TIME + ")" + ");");
                Cursor tempCursor = db.query(tempTableName, null, null, null, null, null,
                        GameContract.Games.COLUMN_NAME_START_TIME);
                while (tempCursor.moveToNext()) {
                    ContentValues values = new ContentValues();
                    final long startTime = tempCursor.getLong(tempCursor.getColumnIndex(
                            GameContract.Games.COLUMN_NAME_START_TIME));
                    values.put(GameContract.Games.COLUMN_NAME_START_TIME, startTime);
                    final int duration = tempCursor.getInt(tempCursor.getColumnIndex(
                            GameContract.Games.COLUMN_NAME_DURATION));
                    values.put(GameContract.Games.COLUMN_NAME_DURATION, duration);
                    final int moves = tempCursor.getInt(tempCursor.getColumnIndex(
                            GameContract.Games.COLUMN_NAME_MOVES));
                    values.put(GameContract.Games.COLUMN_NAME_MOVES, moves);
                    values.put(GameContract.Games.COLUMN_NAME_SYNCED, false);
                    db.insertWithOnConflict(GameContract.Games.TABLE_NAME, GameContract.Games.COLUMN_NAME_START_TIME,
                            values, SQLiteDatabase.CONFLICT_REPLACE);
                }
                tempCursor.close();
                db.execSQL("DROP TABLE " + tempTableName);
            }
        }
    }

    /**
     * The database that the provider uses as its underlying data store
     */
    private static final String DATABASE_NAME = "triplesolitairegames.db";
    /**
     * The database version
     */
    private static final int DATABASE_VERSION = 2;
    /**
     * The incoming URI matches the Game ID URI pattern
     */
    private static final int GAME_ID = 2;
    /**
     * The incoming URI matches the Games URI pattern
     */
    private static final int GAMES = 1;
    /**
     * Used for debugging and logging
     */
    private static final String TAG = "GameProvider";
    /**
     * A UriMatcher instance
     */
    private static final UriMatcher uriMatcher = GameProvider.buildUriMatcher();
    /**
     * Flag to disable change notifications during a batch operation
     */
    private boolean mBatchOperationOngoing = false;
    /**
     * Set of pending notification URIs to send out at the end of the current batch operation
     */
    private LinkedHashSet<Uri> mPendingNotificationUris = new LinkedHashSet<Uri>();

    @Override
    public synchronized ContentProviderResult[] applyBatch(final ArrayList<ContentProviderOperation> operations)
    throws OperationApplicationException {
        mBatchOperationOngoing = true;
        mPendingNotificationUris.clear();
        ContentProviderResult[] results = super.applyBatch(operations);
        mBatchOperationOngoing = false;
        for (Uri uri : mPendingNotificationUris) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return results;
    }

    /**
     * Creates and initializes a column project for all columns
     *
     * @return The all column projection map
     */
    private static HashMap<String, String> buildAllColumnProjectionMap() {
        final HashMap<String, String> allColumnProjectionMap = new HashMap<String, String>();
        allColumnProjectionMap.put(BaseColumns._ID, BaseColumns._ID);
        allColumnProjectionMap
                .put(GameContract.Games.COLUMN_NAME_START_TIME, GameContract.Games.COLUMN_NAME_START_TIME);
        allColumnProjectionMap.put(GameContract.Games.COLUMN_NAME_DURATION, GameContract.Games.COLUMN_NAME_DURATION);
        allColumnProjectionMap.put(GameContract.Games.COLUMN_NAME_MOVES, GameContract.Games.COLUMN_NAME_MOVES);
        allColumnProjectionMap.put(GameContract.Games.COLUMN_NAME_SYNCED, GameContract.Games.COLUMN_NAME_SYNCED);
        return allColumnProjectionMap;
    }

    /**
     * Creates and initializes the URI matcher
     *
     * @return the URI Matcher
     */
    private static UriMatcher buildUriMatcher() {
        final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
        matcher.addURI(GameContract.AUTHORITY, GameContract.Games.TABLE_NAME, GameProvider.GAMES);
        matcher.addURI(GameContract.AUTHORITY, GameContract.Games.TABLE_NAME + "/#", GameProvider.GAME_ID);
        return matcher;
    }

    /**
     * An identity all column projection mapping
     */
    final HashMap<String, String> allColumnProjectionMap = GameProvider.buildAllColumnProjectionMap();
    /**
     * Handle to a new DatabaseHelper.
     */
    private DatabaseHelper databaseHelper;

    @Override
    public int delete(final Uri uri, final String where, final String[] whereArgs) {
        // Opens the database object in "write" mode.
        final SQLiteDatabase db = databaseHelper.getWritableDatabase();
        int count;
        // Does the delete based on the incoming URI pattern.
        switch (GameProvider.uriMatcher.match(uri)) {
            case GAMES:
                // If the incoming pattern matches the general pattern for
                // games, does a delete based on the incoming "where" column and
                // arguments.
                count = db.delete(GameContract.Games.TABLE_NAME, where, whereArgs);
                break;
            case GAME_ID:
                // If the incoming URI matches a single game ID, does the
                // delete based on the incoming data, but modifies the where
                // clause to restrict it to the particular game ID.
                final String finalWhere = DatabaseUtils.concatenateWhere(
                        BaseColumns._ID + " = " + ContentUris.parseId(uri), where);
                count = db.delete(GameContract.Games.TABLE_NAME, finalWhere, whereArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        if (count > 0) {
            if (mBatchOperationOngoing)
                mPendingNotificationUris.add(uri);
            else
                getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    @Override
    public String getType(final Uri uri) {
        /**
         * Chooses the MIME type based on the incoming URI pattern
         */
        switch (GameProvider.uriMatcher.match(uri)) {
            case GAMES:
                // If the pattern is for games, returns the general content
                // type.
                return GameContract.Games.CONTENT_TYPE;
            case GAME_ID:
                // If the pattern is for game IDs, returns the game ID content
                // type.
                return GameContract.Games.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Uri insert(final Uri uri, final ContentValues initialValues) {
        // Validates the incoming URI. Only the full provider URI is allowed for
        // inserts.
        if (GameProvider.uriMatcher.match(uri) != GameProvider.GAMES)
            throw new IllegalArgumentException("Unknown URI " + uri);
        ContentValues values;
        if (initialValues != null)
            values = new ContentValues(initialValues);
        else
            values = new ContentValues();
        if (!values.containsKey(GameContract.Games.COLUMN_NAME_START_TIME))
            values.put(GameContract.Games.COLUMN_NAME_START_TIME, System.currentTimeMillis());
        if (!values.containsKey(GameContract.Games.COLUMN_NAME_SYNCED))
            values.put(GameContract.Games.COLUMN_NAME_SYNCED, false);
        final SQLiteDatabase db = databaseHelper.getWritableDatabase();
        final long rowId = db.insertWithOnConflict(GameContract.Games.TABLE_NAME,
                GameContract.Games.COLUMN_NAME_START_TIME, values, SQLiteDatabase.CONFLICT_IGNORE);
        // If the insert succeeded, the row ID exists.
        if (rowId > 0) {
            // Creates a URI with the game ID pattern and the new row ID appended to it.
            final Uri contractionUri = ContentUris.withAppendedId(GameContract.Games.CONTENT_ID_URI_BASE, rowId);
            if (mBatchOperationOngoing)
                mPendingNotificationUris.add(uri);
            else
                getContext().getContentResolver().notifyChange(uri, null);
            return contractionUri;
        }
        final String startTime = values.getAsString(GameContract.Games.COLUMN_NAME_START_TIME);
        final Cursor existingRow = query(GameContract.Games.CONTENT_ID_URI_BASE, new String[]{BaseColumns._ID},
                GameContract.Games.COLUMN_NAME_START_TIME + "=?", new String[]{startTime}, null);
        if (existingRow.moveToFirst()) {
            Uri gameUri = ContentUris.withAppendedId(GameContract.Games.CONTENT_ID_URI_BASE,
                    existingRow.getLong(existingRow.getColumnIndex(BaseColumns._ID)));
            update(gameUri, values, null, null);
            return gameUri;
        }
        // If the insert didn't succeed and we didn't find an existing row, then something went terribly wrong
        throw new SQLException("Failed to insert row into " + uri);
    }

    /**
     * Creates the underlying DatabaseHelper
     *
     * @see android.content.ContentProvider#onCreate()
     */
    @Override
    public boolean onCreate() {
        databaseHelper = new DatabaseHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(final Uri uri, final String[] projection, final String selection, final String[] selectionArgs,
                        final String sortOrder) {
        // Constructs a new query builder and sets its table name
        final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(GameContract.Games.TABLE_NAME);
        qb.setProjectionMap(allColumnProjectionMap);
        String finalSortOrder = sortOrder;
        if (TextUtils.isEmpty(sortOrder))
            finalSortOrder = GameContract.Games.DEFAULT_SORT_ORDER;
        switch (GameProvider.uriMatcher.match(uri)) {
            case GAMES:
                break;
            case GAME_ID:
                // If the incoming URI is for a single game identified by its
                // ID, appends "_ID = <gameID>" to the where clause, so that it
                // selects that single game
                qb.appendWhere(BaseColumns._ID + "=" + uri.getLastPathSegment());
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        final SQLiteDatabase db = databaseHelper.getReadableDatabase();
        final Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, finalSortOrder, null);
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public int update(final Uri uri, final ContentValues values, final String selection, final String[] selectionArgs) {
        final SQLiteDatabase db = databaseHelper.getWritableDatabase();
        int count;
        switch (GameProvider.uriMatcher.match(uri)) {
            case GAMES:
                // If the incoming URI matches the general games pattern,
                // does the update based on the incoming data.
                count = db.update(GameContract.Games.TABLE_NAME, values, selection, selectionArgs);
                break;
            case GAME_ID:
                // If the incoming URI matches a single game ID, does the
                // update based on the incoming data, but modifies the where
                // clause to restrict it to the particular game ID.
                final String finalWhere = DatabaseUtils.concatenateWhere(
                        BaseColumns._ID + " = " + ContentUris.parseId(uri), selection);
                count = db.update(GameContract.Games.TABLE_NAME, values, finalWhere, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        if (count > 0) {
            if (mBatchOperationOngoing)
                mPendingNotificationUris.add(uri);
            else
                getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }
}