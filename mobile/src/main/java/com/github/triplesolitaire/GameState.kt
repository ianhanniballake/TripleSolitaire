package com.github.triplesolitaire

import android.content.AsyncQueryHandler
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.util.Log
import com.github.triplesolitaire.provider.GameContract
import java.util.ArrayList
import java.util.Random

/**
 * Class to manage the game state associated with a Triple Solitaire game
 */
class GameState(
    /**
     * Activity to issue UI update callbacks
     */
    val activity: GameActivity
) {
    /**
     * Whether a lane should be excluded from autoplay
     * (i.e., if the user just dragged a card from the foundation to that column)
     */
    private var autoplayLaneIndexLocked = BooleanArray(13)

    /**
     * Cards in the Foundation
     */
    private lateinit var foundation: Array<String?>

    /**
     * Current game id as determined by the GameProvider
     */
    var gameId: Long = -1

    /**
     * Whether a game is in progress
     */
    var gameInProgress = false

    /**
     * Handler for asynchronous inserts/updates of games
     */
    private val gameQueryHandler: AsyncQueryHandler = object : AsyncQueryHandler(activity.contentResolver) {
        override fun onInsertComplete(token: Int, cookie: Any, uri: Uri) {
            gameId = ContentUris.parseId(uri)
        }
    }

    /**
     * Increments the game timer is there is at least one move.
     * Posts another copy of itself to trigger in 1 second if
     * the game is in progress.
     */
    private val gameTimerIncrement: Runnable = object : Runnable {
        override fun run() {
            if (moveCount == 0) return
            timeInSeconds++
            activity.updateTime()
            if (gameInProgress) postHandler.postDelayed(this, 1000)
        }
    }

    /**
     * Data (stack and cascade information) for each lane
     */
    private lateinit var lane: Array<LaneData>

    /**
     * To prevent StackOverflow when autoplay animations are off,
     * this Runnable can be used to stagger autoplay calls
     */
    private val moveCompleter = Runnable { moveCompleted() }

    /**
     * Number of player moves in the current game
     */
    var moveCount = 0

    /**
     * A list of all undoable moves
     */
    private lateinit var moves: ArrayDeque<Move>

    /**
     * Number of auto play moves that are pending animation complete
     */
    private var pendingMoves = 0

    /**
     * Handler for running the game timer and move completer
     */
    val postHandler = Handler()

    /**
     * Represents the cards in the stock
     */
    private lateinit var stock: ArrayDeque<String>

    /**
     * How much time has elapsed in the current game
     */
    var timeInSeconds = 0

    /**
     * Represents the cards in the waste
     */
    private lateinit var waste: ArrayDeque<String>

    /**
     * Whether the given lane should accept the given dropped card/cascade
     *
     * @param laneIndex     One-based index (1 through 13) of the lane drop target
     * @param bottomNewCard Bottom card of the cascade/the card to be dropped
     * @return Whether the lane should accept the drop
     */
    fun acceptCascadeDrop(laneIndex: Int, bottomNewCard: String): Boolean {
        val cascadeCard = lane[laneIndex - 1].cascade.last()
        val cascadeSuit = getSuit(cascadeCard)
        val cascadeNum = getNumber(cascadeCard)
        val bottomNewCardSuit = getSuit(bottomNewCard)
        val bottomNewCardNum = getNumber(bottomNewCard)
        val cascadeCardIsBlack = cascadeSuit == "clubs" || cascadeSuit == "spades"
        val bottomNewCardIsBlack = bottomNewCardSuit == "clubs" || bottomNewCardSuit == "spades"
        val acceptDrop = if (bottomNewCardNum != cascadeNum - 1) {
            false
        } else {
            cascadeCardIsBlack && !bottomNewCardIsBlack || !cascadeCardIsBlack && bottomNewCardIsBlack
        }
        if (acceptDrop && BuildConfig.DEBUG) {
            Log.d(
                TAG, 
                "Drag -> $laneIndex: Acceptable drag of $bottomNewCard onto $cascadeCard"
            )
        }
        return acceptDrop
    }

    /**
     * Whether the given foundation should accept the given dropped card.
     * Note, that no multi-card drops are accepted on the foundation
     *
     * @param foundationIndex Negative One-based index (-1 through -12) of the foundation drop target
     * @param newCard         The card to be dropped
     * @return Whether the foundation should accept the drop
     */
    fun acceptFoundationDrop(foundationIndex: Int, newCard: String?): Boolean {
        if (newCard!!.startsWith("MULTI")) {
            // Foundations don't accept multiple cards
            return false
        }
        val existingFoundationCard = foundation[-1 * foundationIndex - 1]
        val acceptDrop = if (existingFoundationCard == null) {
            newCard.endsWith("s1")
        } else {
            newCard == nextInSuit(existingFoundationCard)
        }
        if (acceptDrop && BuildConfig.DEBUG) {
            val foundationDisplayCard = existingFoundationCard ?: "empty foundation"
            Log.d(
                TAG,
                "Drag -> $foundationIndex: Acceptable drag of $newCard onto $foundationDisplayCard"
            )
        }
        return acceptDrop
    }

    /**
     * Adds the given move to the undo stack and updates the UI if this
     * is the first move in the undo stack
     *
     * @param move Move to add to the undo stack
     */
    private fun addMoveToUndo(move: Move) {
        moves.addLast(move)
        if (moves.size == 1) {
            activity.invalidateOptionsMenu()
        }
    }

    /**
     * Callback from the UI to inform us of animation completion
     */
    fun animationCompleted() {
        pendingMoves--
        moveCompleted()
    }

    /**
     * Attempts to auto flip the top stack card in the given lane
     *
     * @param laneIndex One-based index (1 through 13)
     * @return Whether an auto flip was found
     */
    private fun attemptAutoFlip(laneIndex: Int): Boolean {
        if (lane[laneIndex - 1].cascade.isEmpty() && !lane[laneIndex - 1].stack.isEmpty()) {
            move(Move(Move.Type.FLIP, laneIndex))
            return true
        }
        return false
    }

    /**
     * Attempts to auto play the first card in the given cascade to the foundation (moving from -1 to -12 i.e., left to
     * right)
     *
     * @param laneIndex One-based index (1 through 13)
     * @return Whether an auto play was found
     */
    fun attemptAutoMoveFromCascadeToFoundation(laneIndex: Int): Boolean {
        if (lane[laneIndex - 1].cascade.isEmpty()) {
            return false
        }
        val card = lane[laneIndex - 1].cascade.last()
        for (foundationIndex in -1 downTo -12) {
            if (acceptFoundationDrop(foundationIndex, card)) {
                move(Move(Move.Type.AUTO_PLAY, foundationIndex, laneIndex, card))
                return true
            }
        }
        return false
    }

    /**
     * Attempts to auto play the first card in the waste to the foundation
     * (moving from -1 to -12 i.e., left to right)
     *
     * @return Whether an auto play was found
     */
    fun attemptAutoMoveFromWasteToFoundation(): Boolean {
        if (waste.isEmpty()) {
            return false
        }
        val card = waste.first()
        for (foundationIndex in -1 downTo -12) {
            if (acceptFoundationDrop(foundationIndex, card)) {
                move(Move(Move.Type.AUTO_PLAY, foundationIndex, 0, card))
                return true
            }
        }
        return false
    }

    /**
     * Using the user's Autoplay preference, attempts to auto play a single card,
     * first looking through the lanes (from 1 to 13 i.e., left to right)
     * and then to the waste
     */
    private fun autoPlay() {
        if (!gameInProgress) {
            return
        }
        val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
        val autoFlip = preferences.getBoolean(
            Preferences.AUTO_FLIP_PREFERENCE_KEY, activity.resources
                .getBoolean(R.bool.pref_auto_flip_default)
        )
        if (autoFlip) {
            for (laneIndex in 0..12) {
                if (!autoplayLaneIndexLocked[laneIndex] &&
                    attemptAutoFlip(laneIndex + 1)
                ) {
                    return
                }
            }
        }
        val autoPlayMode = preferences.getString(
            Preferences.AUTO_PLAY_PREFERENCE_KEY,
            activity.getString(R.string.pref_auto_play_default)
        )
        // If preference is never we have nothing to do
        if (autoPlayMode == "never") {
            return
        } else if (autoPlayMode == "won") {
            // Check to make sure the user has 'won'
            var totalStackSize = 0
            for (laneIndex in 0..12) {
                totalStackSize += lane[laneIndex].stack.size
            }
            if (totalStackSize > 0 || !stock.isEmpty() || waste.size > 1) {
                return
            }
        }
        // Auto play
        for (laneIndex in 0..12) {
            if (!autoplayLaneIndexLocked[laneIndex] &&
                attemptAutoMoveFromCascadeToFoundation(laneIndex + 1)
            ) {
                return
            }
        }
        attemptAutoMoveFromWasteToFoundation()
    }

    /**
     * Builds a string containing a semicolon separated list of the top
     * numCardsToInclude cards from the given lane
     *
     * @param laneIndex         One-based index (1 through 13)
     * @param numCardsToInclude The number of cards to include
     * @return A semicolon separated list of the cards in the requested cascade
     */
    fun buildCascadeString(laneIndex: Int, numCardsToInclude: Int): String {
        val cascade = lane[laneIndex - 1].cascade
        val cascadeData = StringBuilder(cascade[cascade.size - numCardsToInclude])
        for (cascadeIndex in cascade.size - numCardsToInclude + 1 until cascade.size) {
            cascadeData.append(";")
            cascadeData.append(cascade[cascadeIndex])
        }
        return cascadeData.toString()
    }

    /**
     * Whether there exists a move to undo
     *
     * @return Whether there exists a move to undo
     */
    fun canUndo(): Boolean {
        return moves.isNotEmpty()
    }

    /**
     * Checks to determine if the player has won the game (all foundations have a king).
     * If so, shows the 'you won' dialog.
     */
    private fun checkForWin() {
        for (foundationIndex in 0..11) {
            if (foundation[foundationIndex] == null ||
                !foundation[foundationIndex]!!.endsWith("s13")
            ) {
                return
            }
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Game win detected")
        }
        pauseGame()
        val gameUri = ContentUris.withAppendedId(GameContract.Games.CONTENT_ID_URI_BASE, gameId)
        val values = ContentValues()
        values.put(GameContract.Games.COLUMN_NAME_DURATION, timeInSeconds)
        values.put(GameContract.Games.COLUMN_NAME_MOVES, moveCount)
        gameQueryHandler.startUpdate(0, null, gameUri, values, null, null)
        activity.triggerWin()
    }

    /**
     * Gets the foundation card in the given foundation index
     *
     * @param foundationIndex Negative One-based index (-1 through -12) of the foundation
     * @return the card in the given foundation location
     */
    fun getFoundationCard(foundationIndex: Int): String? {
        return foundation[-1 * foundationIndex - 1]
    }

    /**
     * Gets the requested card from the waste
     *
     * @param wasteIndex Zero-based index of what waste card to return
     * @return The card in the given position in the waste
     */
    fun getWasteCard(wasteIndex: Int) = if (wasteIndex < waste.size) {
        waste[wasteIndex]
    } else {
        null
    }

    /**
     * Whether there are any cards in the stock
     *
     * @return Whether there are any cards in the stock
     */
    val isStockEmpty: Boolean
        get() = stock.isEmpty()

    /**
     * Whether there are any cards in the waste
     *
     * @return Whether there are any cards in the waste
     */
    val isWasteEmpty: Boolean
        get() = waste.isEmpty()

    /**
     * Triggers a move, whether player initiated or an auto play move.
     * Moves are assumed to be valid. Note that moves
     * should have to and from locations in the following format:
     *
     * - Lanes: One-based index (1 through 13)
     * - Waste: 0
     * - Foundation: Negative One-based index (-1 through -12)
     *
     * @param move Move to do
     */
    fun move(move: Move) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, move.toString())
        }
        val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
        when (move.type) {
            Move.Type.STOCK -> {
                if (stock.isEmpty()) {
                    // Flip are cards from the waste over into the stock
                    stock.addAll(waste)
                    waste.clear()
                    addMoveToUndo(move)
                } else {
                    // Move up to 3 cards from the stock to the waste
                    val sb = StringBuilder()
                    var card = stock.removeLast()
                    sb.append(card)
                    waste.addFirst(card)
                    var wasteIndex = 1
                    while (wasteIndex < 3 && !stock.isEmpty()) {
                        card = stock.removeLast()
                        sb.append(';')
                        sb.append(card)
                        waste.addFirst(card)
                        wasteIndex++
                    }
                    addMoveToUndo(Move(Move.Type.STOCK, sb.toString()))
                }
                activity.updateWasteUI()
                activity.updateStockUI()
                moveStarted(true)
                moveCompleted()
            }
            Move.Type.UNDO_STOCK -> {
                if (waste.isEmpty()) {
                    // An empty waste means we had an empty stock right before
                    // the stock click, so we move everything back to the waste
                    waste.addAll(stock)
                    stock.clear()
                } else {
                    // We undo the move of cards from the stock to the waste
                    move.cascade.reversed().forEach { card ->
                        stock.addLast(card)
                        waste.removeFirst()
                    }
                }
                activity.updateWasteUI()
                activity.updateStockUI()
            }
            Move.Type.FLIP -> {
                val toFlip = lane[move.toIndex - 1].stack.removeLast()
                lane[move.toIndex - 1].cascade.add(toFlip)
                addMoveToUndo(move)
                activity.getLane(move.toIndex - 1).flipOverTopStack(toFlip)
                var laneIndex = 0
                while (laneIndex < 13) {
                    autoplayLaneIndexLocked[laneIndex] = false
                    laneIndex++
                }
                autoPlay()
            }
            Move.Type.UNDO_FLIP -> {
                val flippedCard = lane[move.toIndex - 1].cascade.removeFirst()
                lane[move.toIndex - 1].stack.add(flippedCard)
                val newStackSize = lane[move.toIndex - 1].stack.size
                activity.getLane(move.toIndex - 1).setStackSize(newStackSize)
            }
            Move.Type.AUTO_PLAY, Move.Type.UNDO, Move.Type.PLAYER_MOVE -> {
                // Update game state at from location
                when {
                    move.fromIndex < 0 -> {
                        foundation[-1 * move.fromIndex - 1] = prevInSuit(move.card)
                    }
                    move.fromIndex == 0 -> waste.removeFirst()
                    else -> {
                        for (card in move.cascade) {
                            lane[move.fromIndex - 1].cascade.removeLast()
                        }
                    }
                }
                // Update game state at to location
                when {
                    move.toIndex < 0 -> {
                        foundation[-1 * move.toIndex - 1] = move.card
                    }
                    move.toIndex == 0 -> waste.addFirst(move.card)
                    else -> {
                        lane[move.toIndex - 1].cascade.addAll(move.cascade)
                    }
                }
                // Add move to the undo list if it isn't an undo move
                if (move.type != Move.Type.UNDO) {
                    addMoveToUndo(move)
                }
                // Update the from UI
                when {
                    move.fromIndex < 0 -> {
                        activity.updateFoundationUI(-1 * move.fromIndex - 1)
                    }
                    move.fromIndex == 0 -> activity.updateWasteUI()
                    else -> {
                        activity.getLane(move.fromIndex - 1)
                            .decrementCascadeSize(move.cascade.size)
                    }
                }
                // Update the UI
                if (move.type == Move.Type.AUTO_PLAY) {
                    moveStarted(true)
                    val animateAutoplay = preferences.getBoolean(
                        Preferences.ANIMATE_AUTO_PLAY_PREFERENCE_KEY,
                        activity.resources.getBoolean(R.bool.pref_animate_auto_play_default)
                    )
                    if (animateAutoplay) {
                        // Animate an auto play and wait for its completion
                        // before doing anything else. We need to keep track of
                        // how many moves are currently being animated so that
                        // we don't kick off multiple auto plays
                        pendingMoves++
                        activity.animate(move)
                    } else {
                        when {
                            move.toIndex < 0 -> {
                                activity.updateFoundationUI(-1 * move.toIndex - 1)
                            }
                            move.toIndex == 0 -> activity.updateWasteUI()
                            else -> {
                                activity.getLane(move.toIndex - 1)
                                    .addCascade(move.cascade)
                            }
                        }
                        postHandler.post(moveCompleter)
                    }
                } else if (move.type == Move.Type.UNDO) {
                    val animateUndo = preferences.getBoolean(
                        Preferences.ANIMATE_UNDO_PREFERENCE_KEY,
                        activity.resources.getBoolean(R.bool.pref_animate_undo_default)
                    )
                    when {
                        animateUndo -> activity.animate(move)
                        move.toIndex < 0 -> {
                            activity.updateFoundationUI(-1 * move.toIndex - 1)
                        }
                        move.toIndex == 0 -> activity.updateWasteUI()
                        else -> {
                            activity.getLane(move.toIndex - 1)
                                .addCascade(move.cascade)
                        }
                    }
                } else if (move.toIndex < 0) { // PLAYER_MOVE
                    activity.updateFoundationUI(-1 * move.toIndex - 1)
                    moveStarted(true)
                    moveCompleted()
                } else if (move.toIndex == 0) { // PLAYER_MOVE
                    activity.updateWasteUI()
                    moveStarted(true)
                    moveCompleted()
                } else { // PLAYER_MOVE, move.getToIndex > 0
                    if (move.fromIndex < 0) {
                        autoplayLaneIndexLocked[move.toIndex - 1] = true
                    }
                    activity.getLane(move.toIndex - 1).addCascade(move.cascade)
                    moveStarted(move.fromIndex >= 0)
                    moveCompleted()
                }
            }
            else -> {
            }
        }
    }

    /**
     * Signals completion of a move, starting auto play if there are
     * no other pending animations (which will eventually call this method).
     */
    private fun moveCompleted() {
        checkForWin()
        if (pendingMoves == 0) {
            autoPlay()
        }
    }

    /**
     * Signals start of a move, updating the move count, starting the game timer,
     * and resetting the auto play lane locks if requested.
     *
     * @param resetAutoplayLaneIndexLocked Whether to reset the auto play lane locks
     */
    private fun moveStarted(resetAutoplayLaneIndexLocked: Boolean) {
        moveCount++
        activity.updateMoveCount()
        if (moveCount == 1) {
            gameQueryHandler.startInsert(0, null, GameContract.Games.CONTENT_URI, null)
            resumeGame()
        }
        if (resetAutoplayLaneIndexLocked) {
            for (laneIndex in 0..12) {
                autoplayLaneIndexLocked[laneIndex] = false
            }
        }
    }

    /**
     * Starts a new game, resetting the game state and updating the UI to match
     */
    fun newGame() {
        val fullDeck = ArrayList<String>()
        val suitList = arrayOf("clubs", "diamonds", "hearts", "spades")
        for (deckNum in 0..2) {
            for (suit in suitList) {
                for (cardNum in 1..13) {
                    fullDeck.add(suit + cardNum)
                }
            }
        }
        val random = Random()
        random.setSeed(random.nextLong())
        timeInSeconds = 0
        activity.updateTime()
        moveCount = 0
        activity.updateMoveCount()
        for (h in 0..12) {
            autoplayLaneIndexLocked[h] = false
        }
        moves = ArrayDeque()
        activity.invalidateOptionsMenu()
        fullDeck.shuffle(random)
        var currentIndex = 0
        stock = ArrayDeque()
        for (stockIndex in 0..64) {
            stock.addLast(fullDeck[currentIndex++])
        }
        activity.updateStockUI()
        waste = ArrayDeque()
        activity.updateWasteUI()
        foundation = arrayOfNulls(12)
        for (foundationIndex in 0..11) {
            activity.updateFoundationUI(foundationIndex)
        }
        lane = Array(13) { laneIndex ->
            LaneData().apply {
                for (i in 0 until laneIndex) {
                    stack.addLast(fullDeck[currentIndex++])
                }
                cascade.add(fullDeck[currentIndex++])
                val laneLayout = activity.getLane(laneIndex)
                laneLayout.setStackSize(stack.size)
                laneLayout.addCascade(cascade)
            }
        }
    }

    /**
     * Restores the game state from a Bundle and updates the UI to match
     *
     * @param savedInstanceState Bundle to restore from
     */
    fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            return
        }
        // Restore the current game information
        gameId = savedInstanceState.getLong("gameId")
        timeInSeconds = savedInstanceState.getInt("timeInSeconds")
        activity.updateTime()
        moveCount = savedInstanceState.getInt("moveCount")
        activity.updateMoveCount()
        autoplayLaneIndexLocked = savedInstanceState.getBooleanArray("autoplayLaneIndexLocked")!!
        moves = ArrayDeque(savedInstanceState.getStringArrayList("moves")!!.map { move ->
            Move(move)
        })
        // Restore the stack
        stock = ArrayDeque(savedInstanceState.getStringArrayList("stock")!!)
        activity.updateStockUI()
        // Restore the waste data
        waste = ArrayDeque(savedInstanceState.getStringArrayList("waste")!!)
        activity.updateWasteUI()
        // Restore the foundation data
        foundation = savedInstanceState.getStringArray("foundation")!!
        for (foundationIndex in 0..11) activity.updateFoundationUI(foundationIndex)
        lane = Array(13) { laneIndex ->
            LaneData(
                savedInstanceState.getStringArrayList("laneStack$laneIndex")!!,
                savedInstanceState.getStringArrayList("laneCascade$laneIndex")!!
            ).apply {
                val laneLayout = activity.getLane(laneIndex)
                laneLayout.setStackSize(stack.size)
                laneLayout.addCascade(cascade)
            }
        }
        checkForWin()
    }

    /**
     * Saves the current game state to the given Bundle
     *
     * @param outState Bundle to save game state to
     */
    fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("gameId", gameId)
        outState.putInt("timeInSeconds", timeInSeconds)
        outState.putInt("moveCount", moveCount)
        outState.putBooleanArray("autoplayLaneIndexLocked", autoplayLaneIndexLocked)
        val arrayMoves = ArrayList(moves.map { it.toString() })
        outState.putStringArrayList("moves", arrayMoves)
        outState.putStringArrayList("stock", ArrayList(stock))
        outState.putStringArrayList("waste", ArrayList(waste))
        outState.putStringArray("foundation", foundation)
        for (laneIndex in 0..12) {
            outState.putStringArrayList("laneStack$laneIndex", ArrayList(lane[laneIndex].stack))
            outState.putStringArrayList(
                "laneCascade$laneIndex",
                ArrayList(lane[laneIndex].cascade)
            )
        }
    }

    /**
     * Pauses the game, stopping any pending game time increment calls
     */
    fun pauseGame() {
        gameInProgress = false
        postHandler.removeCallbacks(gameTimerIncrement)
        activity.invalidateOptionsMenu()
    }

    /**
     * Resumes the game, starting the game timer increment if there has been at least one move
     */
    fun resumeGame() {
        gameInProgress = moveCount > 0
        if (gameInProgress) {
            postHandler.removeCallbacks(gameTimerIncrement)
            postHandler.postDelayed(gameTimerIncrement, 1000)
        }
        activity.invalidateOptionsMenu()
    }

    /**
     * Undo's the last move
     */
    fun undo() {
        if (moves.isEmpty()) {
            return
        }
        move(moves.removeLast().toUndo())
        if (moves.isEmpty()) {
            activity.invalidateOptionsMenu()
        }
    }

    companion object {
        /**
         * Logging tag
         */
        private const val TAG = "GameActivity"

        /**
         * Whether the empty lane should accept a dropped card/cascade
         *
         * @param laneIndex  One-based index (1 through 13) of the lane drop target
         * @param topNewCard Top card of the cascade/the card to be dropped
         * @return Whether the lane should accept the drop
         */
        fun acceptLaneDrop(laneIndex: Int, topNewCard: String): Boolean {
            val acceptDrop = topNewCard.endsWith("s13")
            if (acceptDrop && BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "Drag -> $laneIndex: Acceptable drag of $topNewCard onto empty lane"
                )
            }
            return acceptDrop
        }

        /**
         * Parses a given card to return the card number (from ace at 1 to king at 13)
         *
         * @param card Card to get the number from
         * @return The number of the card, ranging from ace at 1 to king at 13
         */
        private fun getNumber(card: String): Int {
            return card.substring(card.indexOfFirst { Character.isDigit(it) }).toInt()
        }

        /**
         * Parses a given card to return its suit
         *
         * @param card Card to get the suit from
         * @return The suit of the card
         */
        private fun getSuit(card: String): String {
            return card.substring(0, card.indexOfFirst { Character.isDigit(it) })
        }

        /**
         * Gets the next card (sequentially higher) than the given card
         *
         * @param card Card to 'increment'
         * @return A card of the same suit and one number higher
         */
        private fun nextInSuit(card: String): String {
            return getSuit(card) + (getNumber(card) + 1)
        }

        /**
         * Returns the previous (one number less) card in the same suit as the given card
         *
         * @param card Card to 'decrement'
         * @return A card one number less and in the same suit as the given card, or null if the card was an Ace (number=1)
         */
        private fun prevInSuit(card: String): String? {
            return if (card.endsWith("s1")) {
                null
            } else {
                getSuit(card) + (getNumber(card) - 1)
            }
        }
    }
}