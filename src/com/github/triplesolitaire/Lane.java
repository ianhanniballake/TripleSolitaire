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
import android.widget.RelativeLayout;

public class Lane extends RelativeLayout implements OnDragListener
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
			if (event.getAction() != MotionEvent.ACTION_DOWN)
				return false;
			Log.d(TAG, laneId + ": Starting drag at " + cascadeIndex);
			final StringBuilder cascadeData = new StringBuilder(
					cascade.get(cascadeIndex));
			for (int h = cascadeIndex + 1; h < cascade.size(); h++)
			{
				cascadeData.append(";");
				cascadeData.append(cascade.get(h));
			}
			final ClipData dragData = ClipData.newPlainText(
					cascade.get(cascadeIndex), cascadeData);
			v.startDrag(dragData, new View.DragShadowBuilder(v), laneId, 0);
			return true;
		}
	}

	/**
	 * Logging tag
	 */
	private static final String TAG = "TripleSolitaireActivity";
	private final ArrayList<String> cascade = new ArrayList<String>();
	private int laneId;
	private OnClickListener onCardFlipListener;
	private int stackSize = 0;

	public Lane(final Context context, final AttributeSet attrs)
	{
		super(context, attrs);
		final Card laneBase = new Card(context, R.drawable.lane);
		laneBase.setId(0);
		laneBase.setOnDragListener(this);
		addView(laneBase);
	}

	private boolean acceptDrop(final String cascadeCard, final String newCard)
	{
		int firstNumber;
		for (firstNumber = 0; firstNumber < cascadeCard.length(); firstNumber++)
			if (Character.isDigit(cascadeCard.charAt(firstNumber)))
				break;
		final String currentSuit = cascadeCard.substring(0, firstNumber);
		final int currentNum = Integer.parseInt(cascadeCard
				.substring(firstNumber));
		for (firstNumber = 0; firstNumber < newCard.length(); firstNumber++)
			if (Character.isDigit(newCard.charAt(firstNumber)))
				break;
		final String newCardSuit = newCard.substring(0, firstNumber);
		final int newCardNum = Integer.parseInt(newCard.substring(firstNumber));
		if (newCardNum != currentNum - 1)
			return false;
		if (currentSuit.equals("clubs") && newCardSuit.equals("diamonds"))
			return true;
		else if (currentSuit.equals("clubs") && newCardSuit.equals("hearts"))
			return true;
		else if (currentSuit.equals("clubs") && newCardSuit.equals("spades"))
			return false;
		else if (currentSuit.equals("diamonds") && newCardSuit.equals("clubs"))
			return true;
		else if (currentSuit.equals("diamonds") && newCardSuit.equals("hearts"))
			return false;
		else if (currentSuit.equals("diamonds") && newCardSuit.equals("spades"))
			return true;
		else if (currentSuit.equals("hearts") && newCardSuit.equals("clubs"))
			return true;
		else if (currentSuit.equals("hearts") && newCardSuit.equals("diamonds"))
			return false;
		else if (currentSuit.equals("hearts") && newCardSuit.equals("spades"))
			return true;
		else if (currentSuit.equals("spades") && newCardSuit.equals("clubs"))
			return false;
		else if (currentSuit.equals("spades") && newCardSuit.equals("diamonds"))
			return true;
		else if (currentSuit.equals("spades") && newCardSuit.equals("hearts"))
			return true;
		else
			// same suit
			return false;
	}

	public void addCascade(final ArrayList<String> cascadeToAdd)
	{
		final int card_vert_overlap_dim = getResources().getDimensionPixelSize(
				R.dimen.card_vert_overlap_dim);
		// Create the cascade
		for (int h = 0; h < cascadeToAdd.size(); h++)
		{
			final int cascadeId = h + cascade.size() + stackSize + 1;
			final Card cascadeCard = new Card(getContext(), getResources()
					.getIdentifier(cascadeToAdd.get(h), "drawable",
							getContext().getPackageName()));
			cascadeCard.setId(cascadeId);
			final RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
					android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
					android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
			lp.addRule(RelativeLayout.ALIGN_TOP, cascadeId - 1);
			lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
			if (stackSize > 0)
				lp.setMargins(0, card_vert_overlap_dim, 0, 0);
			cascadeCard.setOnTouchListener(new OnStartDragListener(h
					+ cascade.size()));
			addView(cascadeCard, lp);
		}
		if (cascade.isEmpty() && !cascadeToAdd.isEmpty())
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
		if (!cascade.isEmpty())
		{
			final Card oldTopCascade = (Card) findViewById(stackSize
					+ cascade.size());
			oldTopCascade.setOnDragListener(null);
		}
		if (!cascadeToAdd.isEmpty())
		{
			final Card newTopCascade = (Card) findViewById(getChildCount() - 1);
			newTopCascade.setOnDragListener(this);
		}
		cascade.addAll(cascadeToAdd);
	}

	public void flipOverTopStack(final String card)
	{
		final Card toFlip = (Card) findViewById(stackSize);
		toFlip.setBackgroundResource(getResources().getIdentifier(card,
				"drawable", getContext().getPackageName()));
		toFlip.invalidate();
		toFlip.setOnClickListener(null);
		toFlip.setOnDragListener(this);
		toFlip.setOnTouchListener(new OnStartDragListener(0));
		stackSize = stackSize - 1;
		cascade.add(card);
	}

	@Override
	public boolean onDrag(final View v, final DragEvent event)
	{
		final boolean isMyCascade = laneId == (Integer) event.getLocalState();
		if (event.getAction() == DragEvent.ACTION_DRAG_STARTED)
		{
			if (isMyCascade)
			{
				Log.d(TAG, laneId + ": Drag started of mine: " + event);
				return false;
			}
			final String card = event.getClipDescription().getLabel()
					.toString();
			final String cascadeCard = cascade.get(cascade.size() - 1);
			final boolean acceptDrop = acceptDrop(cascadeCard, card);
			if (acceptDrop)
				Log.d(TAG, laneId + ": Acceptable drag of " + card + " onto "
						+ cascadeCard);
			return acceptDrop;
		}
		else if (event.getAction() == DragEvent.ACTION_DROP)
			Log.d(TAG, laneId + ": Drop of "
					+ event.getClipData().getItemAt(0).getText());
		else if (event.getAction() == DragEvent.ACTION_DRAG_ENDED)
			if (isMyCascade)
				Log.d(TAG,
						laneId + ": Drag ended of mine: " + event.getResult());
		return true;
	}

	public void removeCardsFromCascade(final int numCards)
	{
		if (cascade.size() <= numCards)
		{
			removeViews(stackSize + 1, cascade.size());
			return;
		}
		for (int h = 0; h < numCards; h++)
			removeViewAt(getChildCount() - 1);
	}

	public void restoreUI(final LaneData laneData)
	{
		setStackSize(laneData.getStack().size());
		addCascade(laneData.getCascade());
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
			if (cascade.isEmpty() && stackId == stackSize)
				stackCard.setOnClickListener(onCardFlipListener);
			addView(stackCard, stackId, lp);
		}
		// Recreate the cascade
		addCascade(cascade);
	}
}
