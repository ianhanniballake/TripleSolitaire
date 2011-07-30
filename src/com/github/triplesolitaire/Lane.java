package com.github.triplesolitaire;

import java.util.ArrayList;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

public class Lane extends RelativeLayout
{
	private final ArrayList<String> cascade = new ArrayList<String>();
	private OnClickListener onCardFlipListener;
	private int stackSize = 0;

	public Lane(final Context context, final AttributeSet attrs)
	{
		super(context, attrs);
		final Card laneBase = new Card(context, R.drawable.lane);
		laneBase.setId(0);
		addView(laneBase);
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
			addView(cascadeCard, lp);
		}
		if (cascade.isEmpty() && !cascadeToAdd.isEmpty())
		{
			// Remove the onCardFlipListener from the top card on the stack if
			// there is a cascade now
			final Card topStack = (Card) findViewById(stackSize);
			topStack.setOnClickListener(null);
		}
		cascade.addAll(cascadeToAdd);
	}

	public void flipOverTopStack(final String card)
	{
		final Card toFlip = (Card) findViewById(stackSize);
		toFlip.setBackgroundResource(getResources().getIdentifier(card,
				"drawable", getContext().getPackageName()));
		toFlip.setOnClickListener(null);
		toFlip.invalidate();
		stackSize = stackSize - 1;
		cascade.add(card);
	}

	public void restoreUI(final LaneData laneData)
	{
		setStackSize(laneData.getStack().size());
		addCascade(laneData.getCascade());
	}

	public void setOnCardFlipListener(final OnClickListener onCardFlipListener)
	{
		this.onCardFlipListener = onCardFlipListener;
	}

	public void setStackSize(final int newStackSize)
	{
		// Remove the existing views
		removeViews(1, getChildCount() - 1);
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
