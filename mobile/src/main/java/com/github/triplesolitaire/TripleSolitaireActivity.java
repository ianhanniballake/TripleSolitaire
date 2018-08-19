package com.github.triplesolitaire;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.AsyncQueryHandler;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.os.UserManager;
import android.support.annotation.NonNull;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ViewFlipper;

import com.github.triplesolitaire.provider.GameContract;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.games.AchievementsClient;
import com.google.android.gms.games.AnnotatedData;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.LeaderboardsClient;
import com.google.android.gms.games.SnapshotsClient;
import com.google.android.gms.games.achievement.Achievement;
import com.google.android.gms.games.achievement.AchievementBuffer;
import com.google.android.gms.games.snapshot.Snapshot;
import com.google.android.gms.games.snapshot.SnapshotContents;
import com.google.android.gms.games.snapshot.SnapshotMetadataChange;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Main class which controls the UI of the Triple Solitaire game
 */
public class TripleSolitaireActivity extends Activity implements LoaderCallbacks<Cursor>,
        OnCompleteListener<GoogleSignInAccount>,
        OnSuccessListener<SnapshotsClient.DataOrConflict<Snapshot>> {
    private static final String OUR_SNAPSHOT_ID = "stats.json";
    private static final int REQUEST_SIGN_IN = 0;
    private static final int REQUEST_ACHIEVEMENTS = 1;
    private static final int REQUEST_LEADERBOARDS = 2;
    private static final int REQUEST_GAME = 3;
    /**
     * Logging tag
     */
    private static final String TAG = "TripleSolitaireActivity";
    private ViewFlipper googlePlayGamesViewFlipper;
    private boolean mAlreadyLoadedState = false;
    private boolean mPendingUpdateState = false;
    private StatsState stats = new StatsState();
    private GoogleSignInClient mGoogleSignInClient;
    private GoogleSignInAccount mGoogleSignInAccount;
    private Snapshot mSnapshot;
    private AsyncQueryHandler mAsyncQueryHandler;
    private HandlerThread mPersistStateHandlerThread;
    private Handler mPersistStateHandler;
    private Runnable mPersistStateRunnable = new Runnable() {
        @Override
        public void run() {
            final ArrayList<ContentProviderOperation> operations = stats.getLocalSaveOperations();
            if (BuildConfig.DEBUG)
                Log.d(TripleSolitaireActivity.TAG, "Persisting stats: " + operations.size() + " operations");
            try {
                getContentResolver().applyBatch(GameContract.AUTHORITY, operations);
            } catch (final OperationApplicationException | RemoteException e) {
                Log.e(StatsState.class.getSimpleName(), "Failed persisting stats", e);
            }
            if (BuildConfig.DEBUG)
                Log.d(TripleSolitaireActivity.TAG, "Persisting stats completed");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mPendingUpdateState && GoogleSignIn
                            .getLastSignedInAccount(TripleSolitaireActivity.this) != null) {
                        saveToCloud();
                    }
                }
            });
        }
    };

    public void newGame() {
        startActivityForResult(new Intent(this, GameActivity.class), REQUEST_GAME);
    }

    private synchronized void incrementAchievements(final AchievementBuffer buffer) {
        final String win10 = getString(R.string.achievement_getting_good);
        final String win100 = getString(R.string.achievement_so_youve_played_triple_solitaire);
        final String win250 = getString(R.string.achievement_stop_playing_never);
        for (final Achievement achievement : buffer) {
            final String achievementId = achievement.getAchievementId();
            if (achievement.getType() != Achievement.TYPE_INCREMENTAL)
                continue;
            final int increment = stats.getGamesWon(false) - achievement.getCurrentSteps();
            if (achievementId.equals(win10) && increment > 0)
                Games.getAchievementsClient(this, mGoogleSignInAccount).increment(win10, increment);
            if (achievementId.equals(win100) && increment > 0)
                Games.getAchievementsClient(this, mGoogleSignInAccount).increment(win100, increment);
            if (achievementId.equals(win250) && increment > 0)
                Games.getAchievementsClient(this, mGoogleSignInAccount).increment(win250, increment);
        }
    }

    @Override
    protected void onActivityResult(int request, int response, Intent data) {
        super.onActivityResult(request, response, data);
        if (request == REQUEST_SIGN_IN) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "onActivityResult with requestCode == REQUEST_SIGN_IN, responseCode="
                        + response + ", intent=" + data);
            }
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            GoogleSignInAccount signInAccount = result.getSignInAccount();
            if (result.isSuccess() && signInAccount != null) {
                onConnected(signInAccount);
            } else {
                GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
                final int errorCode = googleApiAvailability.isGooglePlayServicesAvailable(this);
                Dialog dialog = googleApiAvailability.getErrorDialog(this, errorCode, REQUEST_SIGN_IN);
                if (dialog != null) {
                    dialog.show();
                }
            }
        }
        if (request == REQUEST_GAME && response == RESULT_OK) {
            WinDialogFragment winDialogFragment = WinDialogFragment.createInstance(data);
            winDialogFragment.show(getFragmentManager(), "win");
        }
    }

    /**
     * Called when the activity is first created. Sets up the appropriate listeners and starts a new game
     *
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mGoogleSignInClient = GoogleSignIn.getClient(this,
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN)
                .requestScopes(Drive.SCOPE_APPFOLDER)
                .build());
        mAsyncQueryHandler = new AsyncQueryHandler(getContentResolver()) {
        };
        mPersistStateHandlerThread = new HandlerThread(TAG);
        mPersistStateHandlerThread.start();
        mPersistStateHandler = new Handler(mPersistStateHandlerThread.getLooper());
        googlePlayGamesViewFlipper = findViewById(R.id.google_play_games);
        final Button newGameBtn = findViewById(R.id.new_game);
        newGameBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                newGame();
            }
        });
        final Button statsBtn = findViewById(R.id.stats);
        statsBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                StatsDialogFragment.createInstance(stats).show(getFragmentManager(), "stats");
            }
        });
        // Set up sign in button
        final SignInButton signInBtn = findViewById(R.id.sign_in);
        signInBtn.setOnClickListener(new OnClickListener() {
            @SuppressWarnings("synthetic-access")
            @Override
            public void onClick(final View v) {
                startActivityForResult(mGoogleSignInClient.getSignInIntent(), REQUEST_SIGN_IN);
            }
        });
        // Set up Google Play Games buttons
        final Button achievementsBtn = findViewById(R.id.achievements);
        achievementsBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                Games.getAchievementsClient(TripleSolitaireActivity.this, mGoogleSignInAccount)
                        .getAchievementsIntent()
                        .addOnSuccessListener(TripleSolitaireActivity.this,
                                new OnSuccessListener<Intent>() {
                                    @Override
                                    public void onSuccess(final Intent intent) {
                                        startActivityForResult(intent, REQUEST_ACHIEVEMENTS);
                                    }
                                });
            }
        });
        final Button leaderboardsBtn = findViewById(R.id.leaderboards);
        leaderboardsBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                Games.getLeaderboardsClient(TripleSolitaireActivity.this, mGoogleSignInAccount)
                        .getAllLeaderboardsIntent()
                        .addOnSuccessListener(TripleSolitaireActivity.this,
                                new OnSuccessListener<Intent>() {
                                    @Override
                                    public void onSuccess(final Intent intent) {
                                        startActivityForResult(intent, REQUEST_LEADERBOARDS);
                                    }
                                });
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
    protected void onResume() {
        super.onResume();
        mGoogleSignInClient.silentSignIn().addOnCompleteListener(this, this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPersistStateHandlerThread.quit();
    }

    @Override
    public void onLoaderReset(final Loader<Cursor> loader) {
        // Nothing to do
    }

    @Override
    public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
        final int rowCount = data == null ? 0 : data.getCount();
        if (BuildConfig.DEBUG)
            Log.d(TAG, "onLoadFinished found " + rowCount + " rows");
        stats = stats.unionWith(new StatsState(data));
        if (mAlreadyLoadedState && GoogleSignIn.getLastSignedInAccount(this) != null)
            saveToCloud();
        else if (rowCount > 0)
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
                mGoogleSignInClient.signOut();
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
        menu.findItem(R.id.sign_out).setVisible(GoogleSignIn.getLastSignedInAccount(this) != null);
        return true;
    }

    @Override
    public void onComplete(@NonNull final Task<GoogleSignInAccount> signInAccountTask) {
        if (signInAccountTask.isSuccessful()) {
            onConnected(signInAccountTask.getResult());
        } else {
            invalidateOptionsMenu();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && isAccountAccessRestricted()) {
                googlePlayGamesViewFlipper.setDisplayedChild(0);
            } else {
                googlePlayGamesViewFlipper.setDisplayedChild(1);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private boolean isAccountAccessRestricted() {
        UserManager um = (UserManager) getSystemService(Context.USER_SERVICE);
        Bundle restrictions = um.getUserRestrictions();
        return restrictions.getBoolean(UserManager.DISALLOW_MODIFY_ACCOUNTS, false);
    }

    public void onConnected(@NonNull GoogleSignInAccount googleSignInAccount) {
        mGoogleSignInAccount = googleSignInAccount;
        if (BuildConfig.DEBUG)
            Log.d(TripleSolitaireActivity.TAG, "onSignInSucceeded");
        final View title = findViewById(R.id.title);
        Games.getGamesClient(this, mGoogleSignInAccount).setViewForPopups(title);
        invalidateOptionsMenu();
        googlePlayGamesViewFlipper.setDisplayedChild(2);
        if (!mAlreadyLoadedState)
            Games.getSnapshotsClient(this, mGoogleSignInAccount)
                    .open(OUR_SNAPSHOT_ID, false)
                    .addOnSuccessListener(this, this);
        else if (mPendingUpdateState)
            saveToCloud();
    }


    @Override
    public void onSuccess(SnapshotsClient.DataOrConflict<Snapshot> snapshotDataOrConflict) {
        mSnapshot = snapshotDataOrConflict.getData();
        if (!snapshotDataOrConflict.isConflict()) {
            if (BuildConfig.DEBUG)
                Log.d(TripleSolitaireActivity.TAG, "Snapshot loaded successfully");
            // Data was successfully loaded from the cloud: merge with local data.
            try {
                stats = stats.unionWith(new StatsState(mSnapshot.getSnapshotContents().readFully()));
                mAlreadyLoadedState = true;
                mPersistStateHandler.post(mPersistStateRunnable);
            } catch (IOException e) {
                Log.e(TAG, "Error reading snapshot contents", e);
            }
        } else {
            onSnapshotConflict(snapshotDataOrConflict);
        }
    }

    private void onSnapshotConflict(SnapshotsClient.DataOrConflict<Snapshot> openSnapshotResult) {
        SnapshotsClient.SnapshotConflict snapshotConflict = openSnapshotResult.getConflict();
        final String conflictId = snapshotConflict.getConflictId();
        SnapshotContents resolvedSnapshotContents = snapshotConflict
                .getResolutionSnapshotContents();
        try {
            final byte[] localData = resolvedSnapshotContents.readFully();
            final byte[] serverData = openSnapshotResult.getData().getSnapshotContents().readFully();
            if (BuildConfig.DEBUG)
                Log.d(TripleSolitaireActivity.TAG, "onSnapshotConflict");
            // Union the two sets of data together to form a resolved, consistent set of stats
            final StatsState localStats = new StatsState(localData);
            final StatsState serverStats = new StatsState(serverData);
            final StatsState resolvedGame = localStats.unionWith(serverStats);
            SnapshotMetadataChange metadataChange = getUpdatedMetadata(resolvedGame);
            resolvedSnapshotContents.writeBytes(resolvedGame.toBytes());
            Games.getSnapshotsClient(this, mGoogleSignInAccount)
                    .resolveConflict(conflictId, OUR_SNAPSHOT_ID, metadataChange,
                            resolvedSnapshotContents)
                    .addOnSuccessListener(this, this);
        } catch (IOException e) {
            Log.e(TAG, "Error reading snapshot conflict contents", e);
        }
    }

    private SnapshotMetadataChange getUpdatedMetadata(StatsState statsState) {
        final int gamesWon = statsState.getGamesWon(false);
        final int gamesPlayed = statsState.getGamesPlayed();
        String description = getResources().getQuantityString(R.plurals.snapshot_description, gamesWon,
                gamesWon, gamesPlayed);
        return new SnapshotMetadataChange.Builder()
                .setPlayedTimeMillis(stats.getTotalPlayedTimeMillis())
                .setDescription(description)
                .build();
    }

    private synchronized void saveToCloud() {
        if (stats.getGamesUnsynced() > 0) {
            mSnapshot.getSnapshotContents().writeBytes(stats.toBytes());
            SnapshotMetadataChange metadataChange = getUpdatedMetadata(stats);
            SnapshotsClient snapshotsClient = Games.getSnapshotsClient(this, mGoogleSignInAccount);
            snapshotsClient.commitAndClose(mSnapshot, metadataChange);
            mAlreadyLoadedState = false;
            snapshotsClient.open(OUR_SNAPSHOT_ID, true).addOnSuccessListener(this);
        }
        AchievementsClient achievementsClient = Games.getAchievementsClient(this,
                mGoogleSignInAccount);
        LeaderboardsClient leaderboardsClient = Games.getLeaderboardsClient(this,
                mGoogleSignInAccount);
        // Check win streak achievements
        final int longestWinStreak = stats.getLongestWinStreak();
        if (BuildConfig.DEBUG)
            Log.d(TripleSolitaireActivity.TAG, "Longest Win Streak: " + longestWinStreak);
        if (longestWinStreak >= 1)
            achievementsClient.unlock(getString(R.string.achievement_youre_a_winner));
        if (longestWinStreak >= 5)
            achievementsClient.unlock(getString(R.string.achievement_streaker));
        if (longestWinStreak >= 10)
            achievementsClient.unlock(getString(R.string.achievement_master_streaker));
        // Check minimum moves achievements
        final int minimumMovesUnsynced = stats.getMinimumMoves(true);
        if (minimumMovesUnsynced < Integer.MAX_VALUE) {
            if (BuildConfig.DEBUG)
                Log.d(TripleSolitaireActivity.TAG, "Minimum Moves Unsynced: " + minimumMovesUnsynced);
            leaderboardsClient.submitScore(getString(R.string.leaderboard_moves),
                    minimumMovesUnsynced);
        }
        final int minimumMoves = stats.getMinimumMoves(false);
        if (BuildConfig.DEBUG)
            Log.d(TripleSolitaireActivity.TAG, "Minimum Moves: " + minimumMoves);
        if (minimumMoves < 400)
            achievementsClient.unlock(getString(R.string.achievement_figured_it_out));
        if (minimumMoves < 300)
            achievementsClient.unlock(getString(R.string.achievement_no_mistakes));
        // Check shortest time achievements
        final int shortestTimeUnsynced = stats.getShortestTime(true);
        if (shortestTimeUnsynced < Integer.MAX_VALUE) {
            if (BuildConfig.DEBUG)
                Log.d(TripleSolitaireActivity.TAG, "Shortest Time Unsynced (minutes): " +
                        (double) shortestTimeUnsynced / 60);
            leaderboardsClient.submitScore(getString(R.string.leaderboard_time),
                    shortestTimeUnsynced * DateUtils.SECOND_IN_MILLIS);
        }
        final int shortestTime = stats.getShortestTime(false);
        if (BuildConfig.DEBUG)
            Log.d(TripleSolitaireActivity.TAG, "Shortest Time (minutes): " + (double) shortestTime / 60);
        if (shortestTime < 15 * 60)
            achievementsClient.unlock(getString(R.string.achievement_quarter_hour));
        if (shortestTime < 10 * 60)
            achievementsClient.unlock(getString(R.string.achievement_single_digits));
        if (shortestTime < 8 * 60)
            achievementsClient.unlock(getString(R.string.achievement_speed_demon));
        // Send events for newly won games
        final int gamesWonUnsynced = stats.getGamesWon(true);
        if (BuildConfig.DEBUG)
            Log.d(TripleSolitaireActivity.TAG, "Games Won Unsynced: " + gamesWonUnsynced);
        Games.getEventsClient(this, mGoogleSignInAccount)
                .increment(getString(R.string.event_games_won),
                gamesWonUnsynced);
        ContentValues values = new ContentValues();
        values.put(GameContract.Games.COLUMN_NAME_SYNCED, true);
        mAsyncQueryHandler.startUpdate(0, null, GameContract.Games.CONTENT_URI, values,
                GameContract.Games.COLUMN_NAME_SYNCED + "=0", null);
        // Load achievements to handle incremental achievements
        achievementsClient.load(false)
                .addOnSuccessListener(new OnSuccessListener<AnnotatedData<AchievementBuffer>>() {
                    @Override
                    public void onSuccess(AnnotatedData<AchievementBuffer> achievementBufferAnnotatedData) {
                        AchievementBuffer buffer = achievementBufferAnnotatedData.get();
                        if (buffer != null) {
                            incrementAchievements(buffer);
                            buffer.release();
                        }
                    }
                });
        mPendingUpdateState = false;
    }
}