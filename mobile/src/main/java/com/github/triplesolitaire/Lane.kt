package com.github.triplesolitaire

import android.content.ClipData
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.OnDragListener
import android.view.ViewGroup
import android.widget.RelativeLayout

/**
 * Layout to support drawing and managing a lane, including the stack and cascade
 */
class Lane(context: Context, attrs: AttributeSet?) : RelativeLayout(context, attrs),
    OnDragListener {
    /**
     * OnTouchListener used to start a drag event from the cascade
     */
    private inner class OnStartDragListener(
        /**
         * Zero based index of the card in the cascade, where the 0th card is closest
         * to the stack (i.e., under all the rest of the cascade)
         */
        private val cascadeIndex: Int
    ) : OnTouchListener {
        /**
         * Responds to ACTION_DOWN events to start drags of the cascade of the given
         * card and all cards covering it
         *
         * @see android.view.View.OnTouchListener.onTouch
         */
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (event.action != MotionEvent.ACTION_DOWN) {
                return false
            }
            val cascadeData = gameState.buildCascadeString(laneId,
                cascadeSize - cascadeIndex)
            val dragData = ClipData.newPlainText(
                (if (cascadeIndex + 1 != cascadeSize) "MULTI" else "")
                        + cascadeData, cascadeData
            )
            return v.startDrag(dragData, DragShadowBuilder(v), laneId, 0)
        }
    }

    init {
        addView(Card(context, R.drawable.lane).apply {
            id = 0
            setOnDragListener(this@Lane)
        })
    }

    /**
     * Callback for the card flip click events
     */
    lateinit var onCardFlipListener: OnClickListener

    /**
     * The One based index of this lane
     */
    var laneId = 0

    /**
     * Callback to the game state
     */
    lateinit var gameState: GameState

    /**
     * Current size of the cascade
     */
    var cascadeSize = 0

    /**
     * Current size of the stack
     */
    private var stackSize = 0

    /**
     * Adds a new set of cards to the cascade. Creates the new card Views and
     * ensures that the appropriate listeners are set.
     *
     * @param cascadeToAdd List of cards to add to the cascade
     */
    fun addCascade(cascadeToAdd: List<String>) {
        val cardVertOverlapDim = resources.getDimensionPixelSize(R.dimen.card_vert_overlap_dim)
        // Create the cascade
        cascadeToAdd.forEachIndexed { index, cardIdentifier ->
            val cascadeId = index + cascadeSize + stackSize + 1
            val cascadeCard = Card(
                context, resources.getIdentifier(
                    cardIdentifier,
                    "drawable", context.packageName
                )
            ).apply {
                id = cascadeId
                setOnTouchListener(OnStartDragListener(index + cascadeSize))
            }
            val lp = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(ALIGN_TOP, cascadeId - 1)
                addRule(ALIGN_PARENT_LEFT)
                if (stackSize + cascadeSize + index != 0) {
                    setMargins(0, cardVertOverlapDim, 0, 0)
                }
            }
            addView(cascadeCard, lp)
        }
        if (cascadeSize == 0 && cascadeToAdd.isNotEmpty()) {
            if (stackSize > 0) {
                // Remove the onCardFlipListener from the top card on the stack
                // if there is a cascade now
                val topStack: Card = findViewById(stackSize)
                topStack.setOnClickListener(null)
            } else {
                // Remove the onDragListener from the base of the stack if there
                // is a cascade now
                val laneBase: Card = findViewById(0)
                laneBase.setOnDragListener(null)
            }
        }
        if (cascadeSize > 0) {
            val oldTopCascade: Card = findViewById(stackSize + cascadeSize)
            oldTopCascade.setOnDragListener(null)
        }
        if (cascadeToAdd.isNotEmpty()) {
            val newTopCascade: Card = findViewById(childCount - 1)
            newTopCascade.setOnDragListener(this)
        }
        cascadeSize += cascadeToAdd.size
    }

    /**
     * Removes the given number of cards from the cascade.
     *
     * @param removeCount The number of cards to remove from the cascade
     */
    fun decrementCascadeSize(removeCount: Int) {
        for (h in 0 until removeCount) {
            removeViewAt(childCount - 1)
            cascadeSize -= 1
        }
        when {
            stackSize + cascadeSize == 0 -> {
                val laneBase: Card = findViewById(0)
                laneBase.setOnDragListener(this)
            }
            cascadeSize == 0 -> {
                val topStack: Card = findViewById(stackSize)
                topStack.setOnClickListener(onCardFlipListener)
            }
            else -> {
                val topCascade: Card = findViewById(stackSize + cascadeSize)
                topCascade.setOnDragListener(this)
            }
        }
    }

    /**
     * Flips over the top card, replacing the card back image with the given card
     *
     * @param card The card to show as the newly flipped over card
     */
    fun flipOverTopStack(card: String) {
        val toFlip: Card = findViewById(stackSize)
        toFlip.run {
            setBackgroundResource(
                resources.getIdentifier(card, "drawable", context.packageName))
            invalidate()
            setOnClickListener(null)
            setOnDragListener(this@Lane)
            setOnTouchListener(OnStartDragListener(0))
        }
        stackSize -= 1
        cascadeSize += 1
    }

    /**
     * Returns the Card (ImageView) associated with the top
     * (i.e., not covered by any other cards) cascade card
     *
     * @return The Card associated with the top cascade card
     */
    val topCascadeCard: Card  get() = findViewById(childCount - 1)

    /**
     * Responds to drag events of cascades, accepting drops given the right card.
     * If the user fails to drag the card to an appropriate drop point, attempts
     * to auto move it to the foundation
     *
     * @see android.view.View.OnDragListener.onDrag
     */
    override fun onDrag(v: View, event: DragEvent): Boolean {
        val isMyCascade = laneId == event.localState as Int
        if (event.action == DragEvent.ACTION_DRAG_STARTED) {
            var card = event.clipDescription.label.toString()
            if (isMyCascade) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Drag $laneId: Started of $card")
                }
                return true
            }
            // Take off MULTI prefix - we accept all cascades based on the
            // bottom card alone
            card = card.removePrefix("MULTI")
            return if (cascadeSize == 0) {
                GameState.acceptLaneDrop(laneId, card)
            } else {
                gameState.acceptCascadeDrop(laneId, card)
            }
        } else if (event.action == DragEvent.ACTION_DROP && !isMyCascade) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                System.gc()
            }
            val card = event.clipData.getItemAt(0).text.toString()
            val from = event.localState as Int
            gameState.move(Move(Move.Type.PLAYER_MOVE, laneId, from, card))
            return true
        } else if (event.action == DragEvent.ACTION_DROP && isMyCascade) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                System.gc()
            }
            post {
                gameState.attemptAutoMoveFromCascadeToFoundation(laneId)
            }
            return true
        }
        return true
    }

    /**
     * Sets the stack size to the given size. Note that this method also removes
     * all cards from the cascade!
     *
     * @param newStackSize New stack size
     */
    fun setStackSize(newStackSize: Int) {
        // Remove the existing views, including the cascade
        removeViews(1, childCount - 1)
        cascadeSize = 0
        if (stackSize == 0 && newStackSize > 0) {
            val laneBase: Card = findViewById(0)
            laneBase.setOnDragListener(null)
        }
        stackSize = newStackSize
        val cardVertOverlapDim = resources.getDimensionPixelSize(R.dimen.card_vert_overlap_dim)
        // Create the stack
        for (stackId in 1..stackSize) {
            val stackCard = Card(context, R.drawable.back).apply {
                id = stackId
                if (cascadeSize == 0 && stackId == stackSize) {
                    setOnClickListener(onCardFlipListener)
                }
            }
            val lp = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(ALIGN_TOP, stackId - 1)
                addRule(ALIGN_PARENT_LEFT)
                if (stackId != 1) {
                    setMargins(0, cardVertOverlapDim, 0, 0)
                }
            }
            addView(stackCard, stackId, lp)
        }
    }

    companion object {
        /**
         * Logging tag
         */
        private const val TAG = "GameActivity"
    }
}