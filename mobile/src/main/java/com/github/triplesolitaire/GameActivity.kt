package com.github.triplesolitaire

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.TargetApi
import android.app.ActionBar
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.util.Log
import android.view.DragEvent
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.DragShadowBuilder
import android.view.View.OnDragListener
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import com.github.triplesolitaire.Move
import com.github.triplesolitaire.WinDialogFragment.Companion.createDataIntent

/**
 * Main class which controls the UI of the Triple Solitaire game
 */
class GameActivity : Activity() {
    /**
     * Handles Card flip clicks
     */
    private inner class OnCardFlipListener(
        /**
         * One-based index (1 through 13) of the lane clicked
         */
        private val laneIndex: Int
    ) : View.OnClickListener {
        /**
         * Triggers flipping over a card in this lane
         *
         * @see android.view.View.OnClickListener.onClick
         */
        override fun onClick(v: View) {
            gameState.move(Move(Move.Type.FLIP, laneIndex))
        }
    }

    /**
     * Responds to dragging/dropping events on the foundation
     */
    private inner class OnFoundationDragListener(
        /**
         * Negative One-based index (-1 through -12) for the foundation index
         */
        private val foundationIndex: Int
    ) : OnDragListener {
        /**
         * Responds to drag events on the foundation
         *
         * @see android.view.View.OnDragListener.onDrag
         */
        override fun onDrag(v: View, event: DragEvent): Boolean {
            val isMyFoundation = foundationIndex == event.localState as Int
            if (event.action == DragEvent.ACTION_DRAG_STARTED) {
                val foundationCard = gameState.getFoundationCard(foundationIndex)
                if (isMyFoundation) {
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            TAG,
                            "Drag $foundationIndex: Started of $foundationCard"
                        )
                    }
                    return false
                }
                val card = event.clipDescription.label.toString()
                return gameState.acceptFoundationDrop(foundationIndex, card)
            } else if (event.action == DragEvent.ACTION_DROP) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) System.gc()
                val card = event.clipData.getItemAt(0).text.toString()
                val from = event.localState as Int
                gameState.move(Move(Move.Type.PLAYER_MOVE, foundationIndex, from, card))
                return true
            }
            return true
        }
    }

    /**
     * Touch listener used to start a drag event from a foundation
     */
    private inner class OnFoundationTouchListener(
        /**
         * Negative One-based index (-1 through -12)
         */
        private val foundationIndex: Int
    ) : OnTouchListener {
        /**
         * Responds to ACTION_DOWN events to start drags from the foundation
         *
         * @see android.view.View.OnTouchListener.onTouch
         */
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val foundationCard = gameState.getFoundationCard(foundationIndex)
            if (event.action != MotionEvent.ACTION_DOWN || foundationCard == null) {
                return false
            }
            val dragData = ClipData.newPlainText(foundationCard, foundationCard)
            return v.startDrag(dragData, DragShadowBuilder(v), foundationIndex, 0)
        }
    }

    /**
     * Game state which saves and manages the current game
     */
    lateinit var gameState: GameState

    /**
     * Handler used to post delayed calls
     */
    private val handler = Handler()

    /**
     * Animates the given move by creating a copy of the source view and animating it over to the final position before
     * hiding the temporary view and showing the final destination
     *
     * @param move Move to animate
     */
    fun animate(move: Move) {
        val fromLoc: Point = when {
            move.fromIndex < 0 -> getFoundationLoc(-1 * move.fromIndex - 1)
            move.fromIndex == 0 -> wasteLoc
            else -> getCascadeLoc(move.fromIndex - 1)
        }
        val toLoc: Point = when {
            move.toIndex < 0 -> getFoundationLoc(-1 * move.toIndex - 1)
            move.toIndex == 0 -> wasteLoc
            else -> getCascadeLoc(move.toIndex - 1)
        }
        val toAnimate = Card(
            baseContext, resources.getIdentifier(
                move.card, "drawable",
                packageName
            )
        )
        val layout = findViewById<FrameLayout>(R.id.animateLayout).apply {
            addView(toAnimate)
            x = fromLoc.x.toFloat()
            y = fromLoc.y.toFloat()
            visibility = View.VISIBLE
        }
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val animationSpeedPreference = if (move.type == Move.Type.UNDO) {
            preferences.getString(
                Preferences.ANIMATE_SPEED_UNDO_PREFERENCE_KEY,
                null
            ) ?: getString(R.string.pref_animation_speed_undo_default)
        } else {
            preferences.getString(
                Preferences.ANIMATE_SPEED_AUTO_PLAY_PREFERENCE_KEY,
                null
            ) ?: getString(R.string.pref_animation_speed_auto_play_default)
        }
        try {
            layout.animate().x(toLoc.x.toFloat()).y(toLoc.y.toFloat())
                .setDuration(animationSpeedPreference.toLong())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        when {
                            move.toIndex < 0 -> updateFoundationUI(-1 * move.toIndex - 1)
                            move.toIndex == 0 -> updateWasteUI()
                            else -> getLane(move.toIndex - 1).addCascade(move.cascade)
                        }
                        layout.removeAllViews()
                        layout.visibility = View.GONE
                        if (move.type != Move.Type.UNDO) {
                            gameState.animationCompleted()
                        }
                    }
                })
        } catch (e: NullPointerException) {
            // This can occur if the activity is stopped (via screen rotation or
            // other events) on devices before Android 4.0. We handle all state
            // change before the animation, so it is just UI updates that need
            // to be done at this point. As this can only occur if the activity
            // is stopped, the UI will be repainted correctly when the activity
            // resumes, resolving us from any responsibility at this point
        }
    }

    /**
     * Cancel any running animation
     */
    @TargetApi(14)
    private fun cancelAnimation() {
        val layout = findViewById<FrameLayout>(R.id.animateLayout)
        layout.animate().cancel()
    }

    /**
     * Gets the screen location for the top cascade card of the given lane
     *
     * @param laneIndex One-based index (1 through 13) for the lane
     * @return The exact (x,y) position of the top cascade card in the lane
     */
    private fun getCascadeLoc(laneIndex: Int): Point {
        val lane = findViewById<RelativeLayout>(R.id.lane)
        val cascadeView = getLane(laneIndex).topCascadeCard
        val x = (cascadeView.x + cascadeView.paddingLeft + getLane(laneIndex).x
                + getLane(laneIndex).paddingLeft + lane.x + lane.paddingLeft)
        val y = (cascadeView.y + cascadeView.paddingTop + getLane(laneIndex).y
                + getLane(laneIndex).paddingTop + lane.y + lane.paddingTop)
        return Point(x.toInt(), y.toInt())
    }

    /**
     * Gets the screen location for the given foundation
     *
     * @param foundationIndex Negative One-based index (-1 through -12)
     * @return The exact (x,y) position of the foundation
     */
    private fun getFoundationLoc(foundationIndex: Int): Point {
        val foundationLayout = findViewById<RelativeLayout>(R.id.foundation)
        val foundationView = findViewById<ImageView>(
            resources.getIdentifier(
                "foundation" + (foundationIndex + 1), "id", packageName
            )
        )
        val x = (foundationView.x + foundationView.paddingLeft + foundationLayout.x
                + foundationLayout.paddingLeft)
        val y = (foundationView.y + foundationView.paddingTop + foundationLayout.y
                + foundationLayout.paddingTop)
        return Point(x.toInt(), y.toInt())
    }

    /**
     * Gets a string representation (MMM:SS) of the current game time
     *
     * @return Current formatted game time
     */
    private val gameTime: CharSequence
        get() {
            val timeInSeconds = gameState.timeInSeconds
            val minutes = timeInSeconds / 60
            val seconds = timeInSeconds % 60
            val sb = StringBuilder()
            sb.append(minutes)
            sb.append(':')
            if (seconds < 10) {
                sb.append(0)
            }
            sb.append(seconds)
            return sb
        }

    /**
     * Gets the Lane associated with the given index
     *
     * @param laneIndex One-based index (1 through 13) for the lane
     * @return The Lane for the given index
     */
    fun getLane(laneIndex: Int): Lane = findViewById(
        resources.getIdentifier(
            "lane" + (laneIndex + 1),
            "id",
            packageName
        )
    )

    /**
     * Gets the current move count
     *
     * @return Current move count
     */
    private val moveCount: Int
        get() = gameState.moveCount

    /**
     * Gets the screen location for the top card in the waste
     *
     * @return The exact (x,y) position of the top card in the waste
     */
    private val wasteLoc: Point
        get() {
            val waste = findViewById<RelativeLayout>(R.id.waste)
            val waste1View = findViewById<ImageView>(R.id.waste1)
            val x = waste.x + waste.paddingLeft + waste1View.x + waste1View.paddingLeft
            val y = waste.y + waste.paddingTop + waste1View.y + waste1View.paddingTop
            return Point(x.toInt(), y.toInt())
        }

    override fun onBackPressed() {
        val gameStarted = gameState.timeInSeconds > 0
        if (!gameStarted && !gameState.gameInProgress) {
            super.onBackPressed()
            return
        }
        val dialogBuilder = AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert).setTitle(R.string.quit_title)
            .setMessage(R.string.quit_message)
            .setPositiveButton(R.string.quit_positive) { _, _ ->
                super@GameActivity.onBackPressed()
            }
            .setNegativeButton(R.string.quit_negative, null)
        dialogBuilder.show()
    }

    /**
     * Called when the activity is first created. Sets up the appropriate listeners
     * and starts a new game
     *
     * @see android.app.Activity.onCreate
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameState = GameState(this)
        setContentView(R.layout.activity_game)
        // Set up the progress bar area
        val progressBar = layoutInflater.inflate(R.layout.progress_bar, null)
        val bar = actionBar!!
        bar.setDisplayHomeAsUpEnabled(true)
        bar.setDisplayShowCustomEnabled(true)
        val layoutParams = ActionBar.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        layoutParams.gravity = Gravity.LEFT
        bar.setCustomView(progressBar, layoutParams)
        // Set up game listeners
        val stockView = findViewById<ImageView>(R.id.stock)
        stockView.setOnClickListener {
            if (!gameState.isStockEmpty || !gameState.isWasteEmpty) {
                gameState.move(Move(Move.Type.STOCK))
            }
        }
        val wasteTopView = findViewById<ImageView>(R.id.waste1)
        wasteTopView.setOnTouchListener(OnTouchListener { v, event ->
            if (event.action != MotionEvent.ACTION_DOWN || gameState.isWasteEmpty) {
                return@OnTouchListener false
            }
            val dragData = ClipData.newPlainText(
                gameState.getWasteCard(0),
                gameState.getWasteCard(0))
            v.startDrag(dragData, DragShadowBuilder(v), 0, 0)
        })
        wasteTopView.setOnDragListener(OnDragListener { _, event ->
            val fromMe = event.localState as Int == 0
            if (event.action == DragEvent.ACTION_DRAG_STARTED && fromMe) {
                if (BuildConfig.DEBUG) {
                    val card = event.clipDescription.label.toString()
                    Log.d(TAG, "Drag W: Started of $card")
                }
                return@OnDragListener true
            } else if (event.action == DragEvent.ACTION_DRAG_ENDED && !event.result && fromMe) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                    System.gc()
                }
                handler.post {
                    gameState.attemptAutoMoveFromWasteToFoundation()
                }
                return@OnDragListener true
            }
            false
        })
        for (curFoundation in 0..11) {
            val foundationId = resources.getIdentifier(
                "foundation" + (curFoundation + 1), "id",
                packageName
            )
            val foundationLayout = findViewById<ImageView>(foundationId)
            foundationLayout.setOnTouchListener(OnFoundationTouchListener(-1 * (curFoundation + 1)))
            foundationLayout.setOnDragListener(OnFoundationDragListener(-1 * (curFoundation + 1)))
        }
        for (curLane in 0..12) {
            val laneLayout = getLane(curLane)
            laneLayout.onCardFlipListener = OnCardFlipListener(curLane + 1)
            laneLayout.laneId = curLane + 1
            laneLayout.gameState = gameState
        }
        if (savedInstanceState == null) {
            gameState.newGame()
        }
    }

    /**
     * One time method to inflate the options menu / action bar
     *
     * @see android.app.Activity.onCreateOptionsMenu
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.game, menu)
        return true
    }

    /**
     * Called to handle when options menu / action bar buttons are tapped
     *
     * @see android.app.Activity.onOptionsItemSelected
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.undo -> {
                gameState.undo()
                true
            }
            R.id.pause -> {
                GamePauseDialogFragment().show(fragmentManager, "pause")
                true
            }
            R.id.new_game -> {
                gameState.newGame()
                true
            }
            R.id.settings -> {
                startActivity(Intent(this, Preferences::class.java))
                true
            }
            android.R.id.home -> {
                onBackPressed()
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

    override fun onPause() {
        super.onPause()
        cancelAnimation()
    }

    /**
     * Method called every time the options menu is invalidated/repainted.
     * Enables/disables the undo button
     *
     * @see android.app.Activity.onPrepareOptionsMenu
     */
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.undo).isEnabled = gameState.canUndo()
        val gameStarted = gameState.timeInSeconds > 0
        val gameInProgress = gameState.gameInProgress
        menu.findItem(R.id.pause).isEnabled = gameStarted || gameInProgress
        return true
    }

    /**
     * Restores the game state
     *
     * @see android.app.Activity.onRestoreInstanceState
     */
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        gameState.onRestoreInstanceState(savedInstanceState)
        super.onRestoreInstanceState(savedInstanceState)
    }

    /**
     * Saves the game state
     *
     * @see android.app.Activity.onSaveInstanceState
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        gameState.onSaveInstanceState(outState)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onSaveInstanceState")
        }
    }

    /**
     * Pauses/resumes the game timer when window focus is lost/gained, respectively
     *
     * @see android.app.Activity.onWindowFocusChanged
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            gameState.resumeGame()
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        } else {
            gameState.pauseGame()
        }
    }

    fun triggerWin() {
        setResult(RESULT_OK, createDataIntent(gameTime, moveCount))
        finish()
    }

    /**
     * Updates the given foundation UI
     *
     * @param foundationIndex Negative One-based index (-1 through -12) for the foundation
     */
    fun updateFoundationUI(foundationIndex: Int) {
        val foundationCard = gameState.getFoundationCard(-1 * (foundationIndex + 1))
        val foundationViewId = resources.getIdentifier(
            "foundation" + (foundationIndex + 1), "id",
            packageName
        )
        val foundationView = findViewById<ImageView>(foundationViewId)
        if (foundationCard == null) {
            foundationView.setBackgroundResource(R.drawable.foundation)
            foundationView.setOnTouchListener(null)
        } else {
            foundationView.setBackgroundResource(
                resources.getIdentifier(
                    foundationCard, "drawable",
                    packageName
                )
            )
            foundationView.setOnTouchListener(
                OnFoundationTouchListener(-1 * (foundationIndex + 1)))
        }
    }

    /**
     * Updates the move count UI
     */
    fun updateMoveCount() {
        val moveCountView = actionBar!!.customView.findViewById<TextView>(R.id.move_count)
        moveCountView.text = moveCount.toString()
    }

    /**
     * Updates the stock UI
     */
    fun updateStockUI() {
        val stockView = findViewById<ImageView>(R.id.stock)
        if (gameState.isStockEmpty) stockView.setBackgroundResource(R.drawable.lane) else stockView.setBackgroundResource(
            R.drawable.back
        )
    }

    /**
     * Updates the current game time UI
     */
    fun updateTime() {
        val timeView = actionBar!!.customView.findViewById<TextView>(R.id.time)
        timeView.text = gameTime
    }

    /**
     * Updates the waste UI
     */
    fun updateWasteUI() {
        for (wasteIndex in 0..2) updateWasteUI(wasteIndex)
    }

    /**
     * Updates each card in the waste
     *
     * @param wasteIndex Index of the card (0-2)
     */
    private fun updateWasteUI(wasteIndex: Int) {
        val wasteCard = gameState.getWasteCard(wasteIndex)
        val waste = findViewById<ImageView>(
            resources.getIdentifier(
                "waste" + (wasteIndex + 1), "id",
                packageName
            )
        )
        if (wasteCard == null) {
            waste.setBackgroundResource(0)
        } else {
            waste.setBackgroundResource(
                resources.getIdentifier(wasteCard, "drawable", packageName)
            )
        }
    }

    companion object {
        /**
         * Logging tag
         */
        private const val TAG = "GameActivity"
    }
}
