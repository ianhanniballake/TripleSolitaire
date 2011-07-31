package com.github.triplesolitaire;

import java.util.ArrayList;

import android.content.ClipData;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnDragListener;
import android.view.View.OnLongClickListener;
import android.widget.RelativeLayout;

public class Lane extends RelativeLayout implements OnDragListener,
		OnLongClickListener
{
	private class OnStartDragListener implements OnTouchListener
	{
		private final int cascadeIndex;

		public OnStartDragListener(final int cascadeIndex)
		{
			this.cascadeIndex = cascadeIndex;
		}

		@Override
		public boolean onTouch(final View v, final MotionEvent event)
		{
			if (event.getAction() != MotionEvent.ACTION_MOVE)
				return false;
			Log.d(TAG, laneId + ": Starting drag at " + cascadeIndex);
			final String cascadeData = gameState.buildCascadeString(laneId - 1,
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
	private int cascadeSize;
	private GameState gameState;
	private int laneId;
	private OnClickListener onCardFlipListener;
	private int stackSize;

	public Lane(final Context context, final AttributeSet attrs)
	{
		super(context, attrs);
		final Card laneBase = new Card(context, R.drawable.lane);
		laneBase.setId(0);
		laneBase.setOnDragListener(this);
		addView(laneBase);
	}

	public void addCascade(final ArrayList<String> cascadeToAdd)
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
			if (stackSize + cascadeSize > 0)
				lp.setMargins(0, card_vert_overlap_dim, 0, 0);
			cascadeCard.setOnTouchListener(new OnStartDragListener(h
					+ cascadeSize));
			addView(cascadeCard, lp);
		}
		if (cascadeSize == 0 && !cascadeToAdd.isEmpty())
			if (stackSize > 0)
			{
				// Remove the onCardFlipListener from the top card on the stack
				// if
				// there is a cascade now
				final Card topStack = (Card) findViewById(stackSize);
				topStack.setOnClickListener(null);
			}
			else
			{
				// Remove the onDragListener from the base of the stack if
				// there is a cascade now
				final Card laneBase = (Card) findViewById(0);
				laneBase.setOnDragListener(null);
			}
		if (cascadeSize > 0)
		{
			final Card oldTopCascade = (Card) findViewById(stackSize
					+ cascadeSize);
			oldTopCascade.setOnDragListener(null);
			oldTopCascade.setOnLongClickListener(null);
		}
		if (!cascadeToAdd.isEmpty())
		{
			final Card newTopCascade = (Card) findViewById(getChildCount() - 1);
			newTopCascade.setOnDragListener(this);
			newTopCascade.setOnLongClickListener(this);
		}
		cascadeSize += cascadeToAdd.size();
	}

	public void decrementCascadeSize(final int removeCount)
	{
		for (int h = 0; h < removeCount; h++)
		{
			removeViewAt(getChildCount() - 1);
			cascadeSize -= 1;
		}
		if (cascadeSize == 0)
		{
			final Card topStack = (Card) findViewById(stackSize);
			topStack.setOnClickListener(onCardFlipListener);
		}
		else
		{
			final Card topCascade = (Card) findViewById(stackSize + cascadeSize);
			topCascade.setOnLongClickListener(this);
		}
	}

	public void flipOverTopStack(final String card)
	{
		final Card toFlip = (Card) findViewById(stackSize);
		toFlip.setBackgroundResource(getResources().getIdentifier(card,
				"drawable", getContext().getPackageName()));
		toFlip.invalidate();
		toFlip.setOnClickListener(null);
		toFlip.setOnDragListener(this);
		toFlip.setOnLongClickListener(this);
		toFlip.setOnTouchListener(new OnStartDragListener(0));
		stackSize -= 1;
		cascadeSize += 1;
	}

	@Override
	public boolean onDrag(final View v, final DragEvent event)
	{
		final boolean isMyCascade = laneId == (Integer) event.getLocalState();
		if (event.getAction() == DragEvent.ACTION_DRAG_STARTED)
		{
			String card = event.getClipDescription().getLabel().toString();
			if (isMyCascade)
			{
				Log.d(TAG, laneId + ": Drag started of mine of " + card + ": "
						+ event);
				return false;
			}
			// Take off MULTI prefix - we accept all cascades based on the top
			// card alone
			if (card.startsWith("MULTI"))
				card = card.substring(5, card.indexOf(';'));
			final boolean acceptDrop = cascadeSize == 0 ? gameState
					.acceptLaneDrop(card) : gameState.acceptCascadeDrop(
					laneId - 1, card);
			if (acceptDrop)
				Log.d(TAG, laneId + ": Acceptable drag of " + card + " onto "
						+ gameState.getCascadeTop(laneId - 1));
			return acceptDrop;
		}
		else if (event.getAction() == DragEvent.ACTION_DROP)
		{
			final String card = event.getClipData().getItemAt(0).getText()
					.toString();
			Log.d(TAG, laneId + ": Drop of " + card);
			final int from = (Integer) event.getLocalState();
			if (from == 0)
				gameState.dropFromWasteToCascade(laneId - 1);
			else if (from < 0)
				gameState
						.dropFromFoundationToCascade(laneId - 1, -1 * from - 1);
			else
				gameState.dropFromCascadeToCascade(laneId - 1, from - 1, card);
		}
		else if (event.getAction() == DragEvent.ACTION_DRAG_ENDED
				&& isMyCascade)
			Log.d(TAG, laneId + ": Drag ended of mine: " + event.getResult());
		return true;
	}

	@Override
	public boolean onLongClick(final View v)
	{
		gameState.attemptAutoMoveFromCascadeToFoundation(laneId - 1);
		return true;
	}

	public void setGameState(final GameState gameState)
	{
		this.gameState = gameState;
	}

	public void setLaneId(final int laneId)
	{
		this.laneId = laneId;
	}

	public void setOnCardFlipListener(final OnClickListener onCardFlipListener)
	{
		this.onCardFlipListener = onCardFlipListener;
	}

	public void setStackSize(final int newStackSize)
	{
		// Remove the existing views
		removeViews(1, getChildCount() - 1);
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
