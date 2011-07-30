package com.github.triplesolitaire;

import java.util.ArrayList;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

public class Lane extends RelativeLayout
{
	private LaneData laneData;
	private OnClickListener onCardFlipListener;

	public Lane(final Context context, final AttributeSet attrs)
	{
		super(context, attrs);
		final Card laneBase = new Card(context, R.drawable.lane);
		laneBase.setId(0);
		addView(laneBase);
	}

	public void restoreUI(final LaneData newLaneData)
	{
		laneData = newLaneData;
		final int stackSize = laneData.getStack().size();
		final ArrayList<String> cascade = laneData.getCascade();
		removeViews(1, getChildCount() - 1);
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
			addView(stackCard, lp);
		}
		// Create the cascade
		for (int h = 0; h < cascade.size(); h++)
		{
			final int cascadeId = h + stackSize + 1;
			final Card cascadeCard = new Card(getContext(), getResources()
					.getIdentifier(cascade.get(h), "drawable",
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
	}

	public void setOnCardFlipListener(final OnClickListener onCardFlipListener)
	{
		this.onCardFlipListener = onCardFlipListener;
	}
}
