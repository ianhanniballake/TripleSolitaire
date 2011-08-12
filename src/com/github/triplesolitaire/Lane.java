package com.github.triplesolitaire;

import java.util.List;

import android.content.ClipData;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnDragListener;
import android.widget.RelativeLayout;

/**
 * Layout to support drawing and managing a lane, including the stack and
 * cascade
 */
public class Lane extends RelativeLayout implements OnDragListener
{
	/**
	 * OnTouchListener used to start a drag event from the cascade
	 */
	private class OnStartDragListener implements OnTouchListener
	{
		/**
		 * Zero based index of the card in the cascade, where the 0th card is
		 * closest to the stack (i.e., under all the rest of the cascade)
		 */
		private final int cascadeIndex;

		/**
		 * Creates a new listener for the given index
		 * 
		 * @param cascadeIndex
		 *            Zero based index of the cascade card where 0 is the card
		 *            under the rest of the cascade
		 */
		public OnStartDragListener(final int cascadeIndex)
		{
			this.cascadeIndex = cascadeIndex;
		}

		/**
		 * Responds to ACTION_DOWN events to start drags of the cascade of the
		 * given card and all cards covering it
		 * 
		 * @see android.view.View.OnTouchListener#onTouch(android.view.View,
		 *      android.view.MotionEvent)
		 */
		@Override
		public boolean onTouch(final View v, final MotionEvent event)
		{
			if (event.getAction() != MotionEvent.ACTION_DOWN)
				return false;
			final String cascadeData = gameState.buildCascadeString(laneId,
					cascadeSize - cascadeIndex);
			final ClipData dragData = ClipData.newPlainText(
					(cascadeIndex + 1 != cascadeSize ? "MULTI" : "")
							+ cascadeData, cascadeData);
			v.startDrag(dragData, new View.DragShadowBuilder(v), laneId, 0);
			return true;
		}
	}

	/**
	 * Logging tag
	 */
	private static final String TAG = "TripleSolitaireActivity";
	/**
	 * Current size of the cascade
	 */
	private int cascadeSize;
	/**
	 * Callback to the game state
	 */
	private GameState gameState;
	/**
	 * The One based index of this lane
	 */
	private int laneId;
	/**
	 * Callback for the card flip click events
	 */
	private OnClickListener onCardFlipListener;
	/**
	 * Current size of the stack
	 */
	private int stackSize;

	/**
	 * Creates a new Lane, creating the base lane graphic
	 * 
	 * @param context
	 *            Context to create the parent RelativeLayout
	 * @param attrs
	 *            AttributeSet to create the parent RelativeLayout
	 */
	public Lane(final Context context, final AttributeSet attrs)
	{
		super(context, attrs);
		final Card laneBase = new Card(context, R.drawable.lane);
		laneBase.setId(0);
		laneBase.setOnDragListener(this);
		addView(laneBase);
	}

	/**
	 * Adds a new set of cards to the cascade. Creates the new card Views and
	 * ensures that the appropriate listeners are set.
	 * 
	 * @param cascadeToAdd
	 *            List of cards to add to the cascade
	 */
	public void addCascade(final List<String> cascadeToAdd)
	{
		final int card_vert_overlap_dim = getResources().getDimensionPixelSize(
				R.dimen.card_vert_overlap_dim);
		// Create the cascade
		for (int h = 0; h < cascadeToAdd.size(); h++)
		{
			final int cascadeId = h + cascadeSize + stackSize + 1;
			final Card cascadeCard = new Card(getContext(), getResources()
					.getIdentifier(cascadeToAdd.get(h), "drawable",
							getContext().getPackageName()));
			cascadeCard.setId(cascadeId);
			final RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
					android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
					android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
			lp.addRule(RelativeLayout.ALIGN_TOP, cascadeId - 1);
			lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
			if (stackSize + cascadeSize + h != 0)
				lp.setMargins(0, card_vert_overlap_dim, 0, 0);
			cascadeCard.setOnTouchListener(new OnStartDragListener(h
					+ cascadeSize));
			addView(cascadeCard, lp);
		}
		if (cascadeSize == 0 && !cascadeToAdd.isEmpty())
			if (stackSize > 0)
			{
				// Remove the onCardFlipListener from the top card on the stack
				// if there is a cascade now
				final Card topStack = (Card) findViewById(stackSize);
				topStack.setOnClickListener(null);
			}
			else
			{
				// Remove the onDragListener from the base of the stack if there
				// is a cascade now
				final Card laneBase = (Card) findViewById(0);
				laneBase.setOnDragListener(null);
			}
		if (cascadeSize > 0)
		{
			final Card oldTopCascade = (Card) findViewById(stackSize
					+ cascadeSize);
			oldTopCascade.setOnDragListener(null);
		}
		if (!cascadeToAdd.isEmpty())
		{
			final Card newTopCascade = (Card) findViewById(getChildCount() - 1);
			newTopCascade.setOnDragListener(this);
		}
		cascadeSize += cascadeToAdd.size();
	}

	/**
	 * Removes the given number of cards from the cascade.
	 * 
	 * @param removeCount
	 *            The number of cards to remove from the cascade
	 */
	public void decrementCascadeSize(final int removeCount)
	{
		for (int h = 0; h < removeCount; h++)
		{
			removeViewAt(getChildCount() - 1);
			cascadeSize -= 1;
		}
		if (stackSize + cascadeSize == 0)
		{
			final Card laneBase = (Card) findViewById(0);
			laneBase.setOnDragListener(this);
		}
		else if (cascadeSize == 0)
		{
			final Card topStack = (Card) findViewById(stackSize);
			topStack.setOnClickListener(onCardFlipListener);
		}
		else
		{
			final Card topCascade = (Card) findViewById(stackSize + cascadeSize);
			topCascade.setOnDragListener(this);
		}
	}

	/**
	 * Flips over the top card, replacing the card back image with the given
	 * card
	 * 
	 * @param card
	 *            The card to show as the newly flipped over card
	 */
	public void flipOverTopStack(final String card)
	{
		final Card toFlip = (Card) findViewById(stackSize);
		toFlip.setBackgroundResource(getResources().getIdentifier(card,
				"drawable", getContext().getPackageName()));
		toFlip.invalidate();
		toFlip.setOnClickListener(null);
		toFlip.setOnDragListener(this);
		toFlip.setOnTouchListener(new OnStartDragListener(0));
		stackSize -= 1;
		cascadeSize += 1;
	}

	/**
	 * Returns the Card (ImageView) associated with the top (i.e., not covered
	 * by any other cards) cascade card
	 * 
	 * @return The Card associated with the top cascade card
	 */
	public Card getTopCascadeCard()
	{
		return (Card) findViewById(getChildCount() - 1);
	}

	/**
	 * Responds to drag events of cascades, accepting drops given the right
	 * card. If the user fails to drag the card to an appropriate drop point,
	 * attempts to auto move it to the foundation
	 * 
	 * @see android.view.View.OnDragListener#onDrag(android.view.View,
	 *      android.view.DragEvent)
	 */
	@Override
	public boolean onDrag(final View v, final DragEvent event)
	{
		final boolean isMyCascade = laneId == (Integer) event.getLocalState();
		if (event.getAction() == DragEvent.ACTION_DRAG_STARTED)
		{
			String card = event.getClipDescription().getLabel().toString();
			if (isMyCascade)
			{
				Log.d(TAG, "Drag " + laneId + ": Started of " + card);
				return true;
			}
			// Take off MULTI prefix - we accept all cascades based on the
			// bottom card alone
			if (card.startsWith("MULTI"))
				card = card.substring(5, card.indexOf(';'));
			return cascadeSize == 0 ? gameState.acceptLaneDrop(laneId, card)
					: gameState.acceptCascadeDrop(laneId, card);
		}
		else if (event.getAction() == DragEvent.ACTION_DROP && !isMyCascade)
		{
			final String card = event.getClipData().getItemAt(0).getText()
					.toString();
			final int from = (Integer) event.getLocalState();
			gameState.move(new Move(Move.Type.PLAYER_MOVE, laneId, from, card));
			return true;
		}
		else if (event.getAction() == DragEvent.ACTION_DROP && isMyCascade)
		{
			post(new Runnable()
			{
				@Override
				public void run()
				{
					gameState.attemptAutoMoveFromCascadeToFoundation(laneId);
				}
			});
			return true;
		}
		return false;
	}

	/**
	 * Setter for the game state
	 * 
	 * @param gameState
	 *            Game state for call backs
	 */
	public void setGameState(final GameState gameState)
	{
		this.gameState = gameState;
	}

	/**
	 * Setter for the lane id
	 * 
	 * @param laneId
	 *            One based Lane Index
	 */
	public void setLaneId(final int laneId)
	{
		this.laneId = laneId;
	}

	/**
	 * Setter for the flip card click listener
	 * 
	 * @param onCardFlipListener
	 *            Click listener that should be notified of flip clicks
	 */
	public void setOnCardFlipListener(final OnClickListener onCardFlipListener)
	{
		this.onCardFlipListener = onCardFlipListener;
	}

	/**
	 * Sets the stack size to the given size. Note that this method also removes
	 * all cards from the cascade!
	 * 
	 * @param newStackSize
	 *            New stack size
	 */
	public void setStackSize(final int newStackSize)
	{
		// Remove the existing views, including the cascade
		removeViews(1, getChildCount() - 1);
		cascadeSize = 0;
		if (stackSize == 0 && newStackSize > 0)
		{
			final Card laneBase = (Card) findViewById(0);
			laneBase.setOnDragListener(null);
		}
		stackSize = newStackSize;
		final int card_vert_overlap_dim = getResources().getDimensionPixelSize(
				R.dimen.card_vert_overlap_dim);
		// Create the stack
		for (int stackId = 1; stackId <= stackSize; stackId++)
		{
			final Card stackCard = new Card(getContext(), R.drawable.back);
			stackCard.setId(stackId);
			final RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
					android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
					android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
			lp.addRule(RelativeLayout.ALIGN_TOP, stackId - 1);
			lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
			if (stackId != 1)
				lp.setMargins(0, card_vert_overlap_dim, 0, 0);
			if (cascadeSize == 0 && stackId == stackSize)
				stackCard.setOnClickListener(onCardFlipListener);
			addView(stackCard, stackId, lp);
		}
	}
}
