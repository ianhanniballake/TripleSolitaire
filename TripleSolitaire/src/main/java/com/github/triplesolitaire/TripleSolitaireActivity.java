package com.github.triplesolitaire;

import android.app.LoaderManager.LoaderCallbacks;
import android.content.AsyncQueryHandler;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ViewFlipper;

import com.github.triplesolitaire.provider.GameContract;
import com.google.android.gms.appstate.AppStateClient;
import com.google.android.gms.appstate.OnStateLoadedListener;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.games.GamesClient;
import com.google.android.gms.games.achievement.Achievement;
import com.google.android.gms.games.achievement.AchievementBuffer;
import com.google.android.gms.games.achievement.OnAchievementsLoadedListener;
import com.google.example.games.basegameutils.BaseGameActivity;

import java.util.ArrayList;

/**
 * Main class which controls the UI of the Triple Solitaire game
 */
public class TripleSolitaireActivity extends BaseGameActivity implements LoaderCallbacks<Cursor>,
        OnStateLoadedListener, OnAchievementsLoadedListener {
    private static final int OUR_STATE_KEY = 0;
    private static final int REQUEST_ACHIEVEMENTS = 0;
    private static final int REQUEST_LEADERBOARDS = 1;
    /**
     * Logging tag
     */
    private static final String TAG = "TripleSolitaireActivity";
    private ViewFlipper googlePlayGamesViewFlipper;
    private boolean mAlreadyLoadedState = false;
    private boolean mPendingUpdateState = false;
    private StatsState stats = new StatsState();
    private AsyncQueryHandler mAsyncQueryHandler;

    /**
     * Constructs a new TripleSolitaireActivity that uses all Google Play Services
     */
    public TripleSolitaireActivity() {
        // request that superclass initialize and manage the Google Play Services for us
        super(BaseGameActivity.CLIENT_ALL);
        enableDebugLog(BuildConfig.DEBUG, TripleSolitaireActivity.TAG);
    }

    @Override
    public void onAchievementsLoaded(final int statusCode, final AchievementBuffer buffer) {
        switch (statusCode) {
            case GamesClient.STATUS_OK:
                if (BuildConfig.DEBUG)
                    Log.d(TripleSolitaireActivity.TAG, "Achievements loaded successfully");
                // Data was successfully loaded from the cloud, update incremental achievements
                final String win10 = getString(R.string.achievement_getting_good);
                final String win100 = getString(R.string.achievement_so_youve_played_triple_solitaire);
                final String win250 = getString(R.string.achievement_stop_playing_never);
                for (final Achievement achievement : buffer) {
                    final String achievementId = achievement.getAchievementId();
                    if (achievement.getType() != Achievement.TYPE_INCREMENTAL)
                        continue;
                    final int increment = stats.getGamesWon() - achievement.getCurrentSteps();
                    if (achievementId.equals(win10) && increment > 0)
                        getGamesClient().incrementAchievement(win10, increment);
                    if (achievementId.equals(win100) && increment > 0)
                        getGamesClient().incrementAchievement(win100, increment);
                    if (achievementId.equals(win250) && increment > 0)
                        getGamesClient().incrementAchievement(win250, increment);
                }
                break;
            case GamesClient.STATUS_NETWORK_ERROR_NO_DATA:
                if (BuildConfig.DEBUG)
                    Log.d(TripleSolitaireActivity.TAG, "Achievements not loaded - network error with no data");
                // can't reach cloud, and we have no local state.
                // TODO warn about network error
                break;
            case GamesClient.STATUS_NETWORK_ERROR_STALE_DATA:
                if (BuildConfig.DEBUG)
                    Log.d(TripleSolitaireActivity.TAG, "Achievements not loaded - network error with stale data");
                // TODO warn about stale data
                break;
            case GamesClient.STATUS_CLIENT_RECONNECT_REQUIRED:
                if (BuildConfig.DEBUG)
                    Log.d(TripleSolitaireActivity.TAG, "Achievements not loaded - reconnect required");
                // need to reconnect AppStateClient
                reconnectClients(BaseGameActivity.CLIENT_GAMES);
                break;
            default:
                // TODO warn about generic error
                break;
        }
        if (buffer != null)
            buffer.close();
    }

    /**
     * Called when the activity is first created. Sets up the appropriate listeners and starts a new game
     *
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAsyncQueryHandler = new AsyncQueryHandler(getContentResolver()) {
        };
        setContentView(R.layout.main);
        googlePlayGamesViewFlipper = (ViewFlipper) findViewById(R.id.google_play_games);
        final Button newGameBtn = (Button) findViewById(R.id.new_game);
        newGameBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                startActivity(new Intent(TripleSolitaireActivity.this, GameActivity.class));
            }
        });
        final Button statsBtn = (Button) findViewById(R.id.stats);
        statsBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                StatsDialogFragment.createInstance(stats).show(getFragmentManager(), "stats");
            }
        });
        // Set up sign in button
        final SignInButton signInBtn = (SignInButton) findViewById(R.id.sign_in);
        signInBtn.setOnClickListener(new OnClickListener() {
            @SuppressWarnings("synthetic-access")
            @Override
            public void onClick(final View v) {
                beginUserInitiatedSignIn();
            }
        });
        // Set up Google Play Games buttons
        final Button achievementsBtn = (Button) findViewById(R.id.achievements);
        achievementsBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                startActivityForResult(getGamesClient().getAchievementsIntent(), REQUEST_ACHIEVEMENTS);
            }
        });
        final Button leaderboardsBtn = (Button) findViewById(R.id.leaderboards);
        leaderboardsBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                startActivityForResult(getGamesClient().getAllLeaderboardsIntent(), REQUEST_LEADERBOARDS);
            }
        });
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
        return new CursorLoader(this, GameContract.Games.CONTENT_URI, null, null, null, null);
    }

    /**
     * One time method to inflate the options menu / action bar
     *
     * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
     */
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public void onLoaderReset(final Loader<Cursor> loader) {
        // Nothing to do
    }

    @Override
    public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
        stats = stats.unionWith(new StatsState(data));
        if (isSignedIn())
            saveToCloud();
        else if (data.getCount() > 0)
            mPendingUpdateState = true;
    }

    /**
     * Called to handle when options menu / action bar buttons are tapped
     *
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings:
                startActivity(new Intent(this, Preferences.class));
                return true;
            case R.id.sign_out:
                signOut();
                googlePlayGamesViewFlipper.setDisplayedChild(1);
                return true;
            case R.id.about:
                final AboutDialogFragment aboutDialogFragment = new AboutDialogFragment();
                aboutDialogFragment.show(getFragmentManager(), "about");
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Method called every time the options menu is invalidated/repainted. Shows/hides the sign out button
     *
     * @see android.app.Activity#onPrepareOptionsMenu(android.view.Menu)
     */
    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        final boolean isSignedIn = isSignedIn();
        menu.findItem(R.id.sign_out).setVisible(isSignedIn());
        return true;
    }

    @Override
    public void onSignInFailed() {
        if (BuildConfig.DEBUG)
            Log.d(TripleSolitaireActivity.TAG, "onSignInFailed");
        invalidateOptionsMenu();
        googlePlayGamesViewFlipper.setDisplayedChild(1);
    }

    @Override
    public void onSignInSucceeded() {
        if (BuildConfig.DEBUG)
            Log.d(TripleSolitaireActivity.TAG, "onSignInSucceeded");
        invalidateOptionsMenu();
        googlePlayGamesViewFlipper.setDisplayedChild(2);
        if (!mAlreadyLoadedState)
            getAppStateClient().loadState(this, OUR_STATE_KEY);
    }

    @Override
    public void onStateConflict(final int stateKey, final String resolvedVersion, final byte[] localData,
                                final byte[] serverData) {
        if (BuildConfig.DEBUG)
            Log.d(TripleSolitaireActivity.TAG, "onStateConflict");
        // Union the two sets of data together to form a resolved, consistent set of stats
        final StatsState localStats = new StatsState(localData);
        final StatsState serverStats = new StatsState(serverData);
        final StatsState resolvedGame = localStats.unionWith(serverStats);
        getAppStateClient().resolveState(this, OUR_STATE_KEY, resolvedVersion, resolvedGame.toBytes());
    }

    @Override
    public void onStateLoaded(final int statusCode, final int stateKey, final byte[] localData) {
        switch (statusCode) {
            case AppStateClient.STATUS_OK:
                if (BuildConfig.DEBUG)
                    Log.d(TripleSolitaireActivity.TAG, "State loaded successfully");
                // Data was successfully loaded from the cloud: merge with local data.
                stats = stats.unionWith(new StatsState(localData));
                mAlreadyLoadedState = true;
                persistStats();
                if (mPendingUpdateState)
                    saveToCloud();
                break;
            case AppStateClient.STATUS_STATE_KEY_NOT_FOUND:
                if (BuildConfig.DEBUG)
                    Log.d(TripleSolitaireActivity.TAG, "State loaded, no key found");
                // key not found means there is no saved data. To us, this is the same as
                // having empty data, so we treat this as a success.
                mAlreadyLoadedState = true;
                if (mPendingUpdateState)
                    saveToCloud();
                break;
            case AppStateClient.STATUS_NETWORK_ERROR_NO_DATA:
                if (BuildConfig.DEBUG)
                    Log.d(TripleSolitaireActivity.TAG, "State not loaded - network error with no data");
                // can't reach cloud, and we have no local state. Warn user that
                // they may not see their existing progress, but any new progress won't be lost.
                // TODO warn about network error
                break;
            case AppStateClient.STATUS_NETWORK_ERROR_STALE_DATA:
                if (BuildConfig.DEBUG)
                    Log.d(TripleSolitaireActivity.TAG, "State not loaded - network error with stale data");
                // can't reach cloud, but we have locally cached data.
                // TODO warn about stale data
                break;
            case AppStateClient.STATUS_CLIENT_RECONNECT_REQUIRED:
                if (BuildConfig.DEBUG)
                    Log.d(TripleSolitaireActivity.TAG, "State not loaded - reconnect required");
                // need to reconnect AppStateClient
                reconnectClients(BaseGameActivity.CLIENT_APPSTATE);
                break;
            default:
                // TODO warn about generic error
                break;
        }
    }

    private void persistStats() {
        final ArrayList<ContentProviderOperation> operations = stats.getLocalSaveOperations();
        try {
            getContentResolver().applyBatch(GameContract.AUTHORITY, operations);
        } catch (final RemoteException e) {
            Log.e(StatsState.class.getSimpleName(), "Failed persisting stats", e);
        } catch (final OperationApplicationException e) {
            Log.e(StatsState.class.getSimpleName(), "Failed persisting stats", e);
        }
    }

    private void saveToCloud() {
        getAppStateClient().updateState(OUR_STATE_KEY, stats.toBytes());
        final GamesClient gamesClient = getGamesClient();
        // Check win streak achievements
        final int longestWinStreak = stats.getLongestWinStreak();
        if (BuildConfig.DEBUG)
            Log.d(TripleSolitaireActivity.TAG, "Longest Win Streak: " + longestWinStreak);
        if (longestWinStreak > 0)
            gamesClient.unlockAchievement(getString(R.string.achievement_youre_a_winner));
        if (longestWinStreak > 5)
            gamesClient.unlockAchievement(getString(R.string.achievement_streaker));
        if (longestWinStreak > 10)
            gamesClient.unlockAchievement(getString(R.string.achievement_master_streaker));
        // Check minimum moves achievements
        final int minimumMoves = stats.getMinimumMovesUnsynced();
        if (minimumMoves < Integer.MAX_VALUE) {
            if (BuildConfig.DEBUG)
                Log.d(TripleSolitaireActivity.TAG, "Minimum Moves: " + minimumMoves);
            gamesClient.submitScore(getString(R.string.leaderboard_moves), minimumMoves);
        }
        if (minimumMoves < 400)
            gamesClient.unlockAchievement(getString(R.string.achievement_figured_it_out));
        if (minimumMoves < 300)
            gamesClient.unlockAchievement(getString(R.string.achievement_no_mistakes));
        // Check shortest time achievements
        final int shortestTime = stats.getShortestTimeUnsynced();
        if (shortestTime < Integer.MAX_VALUE) {
            if (BuildConfig.DEBUG)
                Log.d(TripleSolitaireActivity.TAG, "Shortest Time (minutes): " + (double) shortestTime / 60);
            gamesClient.submitScore(getString(R.string.leaderboard_time), shortestTime * DateUtils.SECOND_IN_MILLIS);
        }
        if (shortestTime < 15 * 60)
            gamesClient.unlockAchievement(getString(R.string.achievement_quarter_hour));
        if (shortestTime < 10 * 60)
            gamesClient.unlockAchievement(getString(R.string.achievement_single_digits));
        if (shortestTime < 8 * 60)
            gamesClient.unlockAchievement(getString(R.string.achievement_speed_demon));
        ContentValues values = new ContentValues();
        values.put(GameContract.Games.COLUMN_NAME_SYNCED, true);
        mAsyncQueryHandler.startUpdate(0, null, GameContract.Games.CONTENT_URI, values,
                GameContract.Games.COLUMN_NAME_SYNCED + "=0", null);
        // Load achievements to handle incremental achievements
        gamesClient.loadAchievements(this, false);
        mPendingUpdateState = false;
    }
}