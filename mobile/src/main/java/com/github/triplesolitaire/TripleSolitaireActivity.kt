package com.github.triplesolitaire

import android.annotation.TargetApi
import android.app.Activity
import android.app.LoaderManager
import android.content.AsyncQueryHandler
import android.content.ContentValues
import android.content.CursorLoader
import android.content.Intent
import android.content.Loader
import android.content.OperationApplicationException
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.RemoteException
import android.os.UserManager
import android.text.format.DateUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ViewFlipper
import com.github.triplesolitaire.StatsDialogFragment.Companion.createInstance
import com.github.triplesolitaire.WinDialogFragment.Companion.createInstance
import com.github.triplesolitaire.provider.GameContract
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.SignInButton
import com.google.android.gms.drive.Drive
import com.google.android.gms.games.Games
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.SnapshotsClient.DataOrConflict
import com.google.android.gms.games.achievement.Achievement
import com.google.android.gms.games.achievement.AchievementBuffer
import com.google.android.gms.games.snapshot.Snapshot
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import java.io.IOException

/**
 * Main class which controls the UI of the Triple Solitaire game
 */
class TripleSolitaireActivity : Activity(), LoaderManager.LoaderCallbacks<Cursor?>,
    OnCompleteListener<GoogleSignInAccount>, OnSuccessListener<DataOrConflict<Snapshot?>> {
    private lateinit var googlePlayGamesViewFlipper: ViewFlipper
    private var alreadyLoadedState = false
    private var pendingUpdateState = false
    private var stats = StatsState()
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInAccount: GoogleSignInAccount
    private lateinit var snapshot: Snapshot
    private lateinit var asyncQueryHandler: AsyncQueryHandler
    private lateinit var persistStateHandlerThread: HandlerThread
    private lateinit var persistStateHandler: Handler
    private val persistStateRunnable = Runnable {
        val operations = stats.localSaveOperations
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Persisting stats: ${operations.size} operations")
        }
        try {
            contentResolver.applyBatch(GameContract.AUTHORITY, operations)
        } catch (e: OperationApplicationException) {
            Log.e(StatsState::class.java.simpleName, "Failed persisting stats", e)
        } catch (e: RemoteException) {
            Log.e(StatsState::class.java.simpleName, "Failed persisting stats", e)
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Persisting stats completed")
        }
        runOnUiThread {
            if (pendingUpdateState && GoogleSignIn
                    .getLastSignedInAccount(this@TripleSolitaireActivity) != null
            ) {
                saveToCloud()
            }
        }
    }

    fun newGame() {
        startActivityForResult(Intent(this, GameActivity::class.java), REQUEST_GAME)
    }

    @Synchronized
    private fun incrementAchievements(buffer: AchievementBuffer) {
        val win10 = getString(R.string.achievement_getting_good)
        val win100 = getString(R.string.achievement_so_youve_played_triple_solitaire)
        val win250 = getString(R.string.achievement_stop_playing_never)
        for (achievement in buffer) {
            val achievementId = achievement.achievementId
            if (achievement.type != Achievement.TYPE_INCREMENTAL) {
                continue
            }
            val increment = stats.getGamesWon(false) - achievement.currentSteps
            if (achievementId == win10 && increment > 0) {
                Games.getAchievementsClient(this, googleSignInAccount).increment(win10, increment)
            }
            if (achievementId == win100 && increment > 0) {
                Games.getAchievementsClient(this, googleSignInAccount).increment(win100, increment)
            }
            if (achievementId == win250 && increment > 0) {
                Games.getAchievementsClient(this, googleSignInAccount).increment(win250, increment)
            }
        }
    }

    override fun onActivityResult(request: Int, response: Int, data: Intent) {
        super.onActivityResult(request, response, data)
        if (request == REQUEST_SIGN_IN) {
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "onActivityResult with requestCode == REQUEST_SIGN_IN, responseCode=$response, intent=$data"
                )
            }
            val result = Auth.GoogleSignInApi.getSignInResultFromIntent(data)
            val signInAccount = result!!.signInAccount
            if (result.isSuccess && signInAccount != null) {
                onConnected(signInAccount)
            } else {
                val googleApiAvailability = GoogleApiAvailability.getInstance()
                val errorCode = googleApiAvailability.isGooglePlayServicesAvailable(this)
                val dialog = googleApiAvailability.getErrorDialog(this, errorCode, REQUEST_SIGN_IN)
                dialog?.show()
            }
        }
        if (request == REQUEST_GAME && response == RESULT_OK) {
            val winDialogFragment = createInstance(data)
            winDialogFragment.show(fragmentManager, "win")
        }
    }

    /**
     * Called when the activity is first created. Sets up the appropriate listeners
     * and starts a new game
     *
     * @see android.app.Activity.onCreate
     */
    override fun onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        googleSignInClient = GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN)
                .requestScopes(Drive.SCOPE_APPFOLDER)
                .build()
        )
        asyncQueryHandler = object : AsyncQueryHandler(contentResolver) {}
        persistStateHandlerThread = HandlerThread(TAG)
        persistStateHandlerThread.start()
        persistStateHandler = Handler(persistStateHandlerThread.looper)
        googlePlayGamesViewFlipper = findViewById(R.id.google_play_games)
        findViewById<Button>(R.id.new_game).setOnClickListener {
            newGame()
        }
        findViewById<Button>(R.id.stats).setOnClickListener {
            createInstance(stats).show(fragmentManager, "stats")
        }
        // Set up sign in button
        findViewById<SignInButton>(R.id.sign_in).setOnClickListener {
            startActivityForResult(
                googleSignInClient.signInIntent,
                REQUEST_SIGN_IN
            )
        }
        // Set up Google Play Games buttons
        findViewById<Button>(R.id.achievements).setOnClickListener {
            Games.getAchievementsClient(this@TripleSolitaireActivity, googleSignInAccount)
                .achievementsIntent
                .addOnSuccessListener(this@TripleSolitaireActivity) { intent ->
                    startActivityForResult(intent, REQUEST_ACHIEVEMENTS)
                }
        }
        findViewById<Button>(R.id.leaderboards).setOnClickListener {
            Games.getLeaderboardsClient(this@TripleSolitaireActivity, googleSignInAccount)
                .allLeaderboardsIntent
                .addOnSuccessListener(this@TripleSolitaireActivity) { intent ->
                    startActivityForResult(intent, REQUEST_LEADERBOARDS)
                }
        }
        loaderManager.initLoader(0, null, this)
    }

    override fun onCreateLoader(id: Int, args: Bundle): Loader<Cursor?> {
        return CursorLoader(this, GameContract.Games.CONTENT_URI, null, null, null, null)
    }

    /**
     * One time method to inflate the options menu / action bar
     *
     * @see android.app.Activity.onCreateOptionsMenu
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onResume() {
        super.onResume()
        googleSignInClient.silentSignIn().addOnCompleteListener(this, this)
    }

    override fun onDestroy() {
        super.onDestroy()
        persistStateHandlerThread.quit()
    }

    override fun onLoaderReset(loader: Loader<Cursor?>) {
        // Nothing to do
    }

    override fun onLoadFinished(loader: Loader<Cursor?>, data: Cursor?) {
        val rowCount = data?.count ?: 0
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onLoadFinished found $rowCount rows")
        }
        stats = stats.unionWith(StatsState(data))
        if (alreadyLoadedState && GoogleSignIn.getLastSignedInAccount(this) != null) {
            saveToCloud()
        } else if (rowCount > 0) {
            pendingUpdateState = true
        }
    }

    /**
     * Called to handle when options menu / action bar buttons are tapped
     *
     * @see android.app.Activity.onOptionsItemSelected
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings -> {
                startActivity(Intent(this, Preferences::class.java))
                true
            }
            R.id.sign_out -> {
                googleSignInClient.signOut()
                googlePlayGamesViewFlipper.displayedChild = 1
                true
            }
            R.id.about -> {
                val aboutDialogFragment = AboutDialogFragment()
                aboutDialogFragment.show(fragmentManager, "about")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Method called every time the options menu is invalidated/repainted.
     * Shows/hides the sign out button
     *
     * @see android.app.Activity.onPrepareOptionsMenu
     */
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.sign_out).isVisible =
            GoogleSignIn.getLastSignedInAccount(this) != null
        return true
    }

    override fun onComplete(signInAccountTask: Task<GoogleSignInAccount>) {
        if (signInAccountTask.isSuccessful) {
            onConnected(signInAccountTask.result)
        } else {
            invalidateOptionsMenu()
            googlePlayGamesViewFlipper.displayedChild = if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 &&
                isAccountAccessRestricted
            ) {
                0
            } else {
                1
            }
        }
    }

    @get:TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private val isAccountAccessRestricted: Boolean
        get() {
            val um = getSystemService(USER_SERVICE) as UserManager
            val restrictions = um.userRestrictions
            return restrictions.getBoolean(UserManager.DISALLOW_MODIFY_ACCOUNTS, false)
        }

    private fun onConnected(account: GoogleSignInAccount) {
        googleSignInAccount = account
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onSignInSucceeded")
        }
        val title = findViewById<View>(R.id.title)
        Games.getGamesClient(this, googleSignInAccount).setViewForPopups(title)
        invalidateOptionsMenu()
        googlePlayGamesViewFlipper.displayedChild = 2
        if (!alreadyLoadedState) {
            Games.getSnapshotsClient(this, googleSignInAccount)
                .open(OUR_SNAPSHOT_ID, false)
                .addOnSuccessListener(this, this)
        } else if (pendingUpdateState) {
            saveToCloud()
        }
    }

    override fun onSuccess(snapshotDataOrConflict: DataOrConflict<Snapshot?>) {
        if (!snapshotDataOrConflict.isConflict) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Snapshot loaded successfully")
            }
            // Data was successfully loaded from the cloud: merge with local data.
            try {
                snapshot = snapshotDataOrConflict.data!!
                stats = stats.unionWith(StatsState(snapshot.snapshotContents.readFully()))
                alreadyLoadedState = true
                persistStateHandler.post(persistStateRunnable)
            } catch (e: IOException) {
                Log.e(TAG, "Error reading snapshot contents", e)
            }
        } else {
            onSnapshotConflict(snapshotDataOrConflict.conflict!!)
        }
    }

    private fun onSnapshotConflict(snapshotConflict: SnapshotsClient.SnapshotConflict) {
        val conflictId = snapshotConflict.conflictId
        val resolvedSnapshotContents = snapshotConflict.resolutionSnapshotContents
        try {
            val localData = snapshotConflict.conflictingSnapshot.snapshotContents.readFully()
            val serverData = snapshotConflict.snapshot.snapshotContents.readFully()
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "onSnapshotConflict")
            }
            // Union the two sets of data together to form a resolved, consistent set of stats
            val localStats = StatsState(localData)
            val serverStats = StatsState(serverData)
            val resolvedGame = localStats.unionWith(serverStats)
            val metadataChange = getUpdatedMetadata(resolvedGame)
            resolvedSnapshotContents.writeBytes(resolvedGame.toBytes())
            Games.getSnapshotsClient(this, googleSignInAccount).resolveConflict(
                conflictId, OUR_SNAPSHOT_ID, metadataChange, resolvedSnapshotContents
            ).addOnSuccessListener(this, this)
        } catch (e: IOException) {
            Log.e(TAG, "Error reading snapshot conflict contents", e)
        }
    }

    private fun getUpdatedMetadata(statsState: StatsState): SnapshotMetadataChange {
        val gamesWon = statsState.getGamesWon(false)
        val gamesPlayed = statsState.gamesPlayed
        val description = resources.getQuantityString(
            R.plurals.snapshot_description, gamesWon,
            gamesWon, gamesPlayed
        )
        return SnapshotMetadataChange.Builder()
            .setPlayedTimeMillis(stats.totalPlayedTimeMillis)
            .setDescription(description)
            .build()
    }

    @Synchronized
    private fun saveToCloud() {
        if (stats.gamesUnsynced > 0) {
            snapshot.snapshotContents.writeBytes(stats.toBytes())
            val metadataChange = getUpdatedMetadata(stats)
            val snapshotsClient = Games.getSnapshotsClient(this, googleSignInAccount)
            snapshotsClient.commitAndClose(snapshot, metadataChange)
            alreadyLoadedState = false
            snapshotsClient.open(OUR_SNAPSHOT_ID, true).addOnSuccessListener(this)
        }
        val achievementsClient = Games.getAchievementsClient(
            this,
            googleSignInAccount
        )
        val leaderboardsClient = Games.getLeaderboardsClient(
            this,
            googleSignInAccount
        )
        // Check win streak achievements
        val longestWinStreak = stats.longestWinStreak
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Longest Win Streak: $longestWinStreak")
        }
        if (longestWinStreak >= 1) {
            achievementsClient.unlock(getString(R.string.achievement_youre_a_winner))
        }
        if (longestWinStreak >= 5) {
            achievementsClient.unlock(getString(R.string.achievement_streaker))
        }
        if (longestWinStreak >= 10) {
            achievementsClient.unlock(getString(R.string.achievement_master_streaker))
        }
        // Check minimum moves achievements
        val minimumMovesUnsynced = stats.getMinimumMoves(true)
        if (minimumMovesUnsynced < Int.MAX_VALUE) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Minimum Moves Unsynced: $minimumMovesUnsynced")
            }
            leaderboardsClient.submitScore(
                getString(R.string.leaderboard_moves),
                minimumMovesUnsynced.toLong()
            )
        }
        val minimumMoves = stats.getMinimumMoves(false)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Minimum Moves: $minimumMoves")
        }
        if (minimumMoves < 400) {
            achievementsClient.unlock(getString(R.string.achievement_figured_it_out))
        }
        if (minimumMoves < 300) {
            achievementsClient.unlock(getString(R.string.achievement_no_mistakes))
        }
        // Check shortest time achievements
        val shortestTimeUnsynced = stats.getShortestTime(true)
        if (shortestTimeUnsynced < Int.MAX_VALUE) {
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG, "Shortest Time Unsynced (minutes): " +
                            shortestTimeUnsynced.toDouble() / 60
                )
            }
            leaderboardsClient.submitScore(
                getString(R.string.leaderboard_time),
                shortestTimeUnsynced * DateUtils.SECOND_IN_MILLIS
            )
        }
        val shortestTime = stats.getShortestTime(false)
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "Shortest Time (minutes): " + shortestTime.toDouble() / 60
            )
        }
        if (shortestTime < 15 * 60) {
            achievementsClient.unlock(getString(R.string.achievement_quarter_hour))
        }
        if (shortestTime < 10 * 60) {
            achievementsClient.unlock(getString(R.string.achievement_single_digits))
        }
        if (shortestTime < 8 * 60) {
            achievementsClient.unlock(getString(R.string.achievement_speed_demon))
        }
        // Send events for newly won games
        val gamesWonUnsynced = stats.getGamesWon(true)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Games Won Unsynced: $gamesWonUnsynced")
        }
        Games.getEventsClient(this, googleSignInAccount).increment(
            getString(R.string.event_games_won),
            gamesWonUnsynced
        )
        val values = ContentValues()
        values.put(GameContract.Games.COLUMN_NAME_SYNCED, true)
        asyncQueryHandler.startUpdate(
            0, null, GameContract.Games.CONTENT_URI, values,
            GameContract.Games.COLUMN_NAME_SYNCED + "=0", null
        )
        // Load achievements to handle incremental achievements
        achievementsClient.load(false).addOnSuccessListener { achievementBufferAnnotatedData ->
            val buffer = achievementBufferAnnotatedData.get()
            if (buffer != null) {
                incrementAchievements(buffer)
                buffer.release()
            }
        }
        pendingUpdateState = false
    }

    companion object {
        private const val OUR_SNAPSHOT_ID = "stats.json"
        private const val REQUEST_SIGN_IN = 0
        private const val REQUEST_ACHIEVEMENTS = 1
        private const val REQUEST_LEADERBOARDS = 2
        private const val REQUEST_GAME = 3

        /**
         * Logging tag
         */
        private const val TAG = "TripleSolitaireActivity"
    }
}