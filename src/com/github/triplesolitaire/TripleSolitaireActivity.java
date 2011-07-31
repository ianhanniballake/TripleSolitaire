package com.github.triplesolitaire;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnDragListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.TextView;

public class TripleSolitaireActivity extends Activity
{
	private class OnCardFlipListener implements OnClickListener
	{
		private final int laneIndex;

		public OnCardFlipListener(final int laneIndex)
		{
			this.laneIndex = laneIndex;
		}

		@Override
		public void onClick(final View v)
		{
			Log.d(TAG, "Clicked " + (laneIndex + 1));
			gameState.flipCard(laneIndex);
		}
	}

	private class OnFoundationDragListener implements OnDragListener
	{
		private final int foundationIndex;

		public OnFoundationDragListener(final int foundationIndex)
		{
			this.foundationIndex = foundationIndex;
		}

		@Override
		public boolean onDrag(final View v, final DragEvent event)
		{
			final boolean isMyFoundation = -1 * foundationIndex == (Integer) event
					.getLocalState();
			if (event.getAction() == DragEvent.ACTION_DRAG_STARTED)
			{
				final String foundationCard = gameState
						.getFoundationCard(foundationIndex - 1);
				if (isMyFoundation)
				{
					Log.d(TAG, -1 * foundationIndex
							+ ": Drag started of mine of " + foundationCard
							+ ": " + event);
					return false;
				}
				final String card = event.getClipDescription().getLabel()
						.toString();
				final boolean acceptDrop = gameState.acceptFoundationDrop(
						foundationIndex - 1, card);
				if (acceptDrop)
					Log.d(TAG, -1 * foundationIndex + ": Acceptable drag of "
							+ card + " onto " + foundationCard);
				return acceptDrop;
			}
			else if (event.getAction() == DragEvent.ACTION_DROP)
			{
				final String card = event.getClipData().getItemAt(0).getText()
						.toString();
				Log.d(TAG, -1 * foundationIndex + ": Drop of " + card);
				final int from = (Integer) event.getLocalState();
				if (from == 0)
					gameState.dropFromWasteToFoundation(foundationIndex - 1);
				else if (from < 0)
					gameState.dropFromFoundationToFoundation(
							foundationIndex - 1, -1 * from - 1);
				else
					gameState.dropFromCascadeToFoundation(foundationIndex - 1,
							from - 1);
			}
			else if (event.getAction() == DragEvent.ACTION_DRAG_ENDED)
				if (isMyFoundation)
					Log.d(TAG, -1 * foundationIndex + ": Drag ended of mine: "
							+ event.getResult());
			return true;
		}
	}

	private class OnFoundationTouchListener implements OnTouchListener
	{
		private final int foundationIndex;

		public OnFoundationTouchListener(final int foundationIndex)
		{
			this.foundationIndex = foundationIndex;
		}

		@Override
		public boolean onTouch(final View v, final MotionEvent event)
		{
			final String foundationCard = gameState
					.getFoundationCard(foundationIndex - 1);
			if (event.getAction() != MotionEvent.ACTION_DOWN
					|| foundationCard == null)
				return false;
			Log.d(TAG, -1 * foundationIndex + ": Starting drag at foundation");
			final ClipData dragData = ClipData.newPlainText(foundationCard,
					foundationCard);
			v.startDrag(dragData, new View.DragShadowBuilder(v), -1
					* foundationIndex, 0);
			return true;
		}
	}

	public static final int DIALOG_ID_SHOW_GAME_ID = 1;
	public static final int DIALOG_ID_WINNING = 0;
	/**
	 * Logging tag
	 */
	private static final String TAG = "TripleSolitaireActivity";
	private final GameState gameState = new GameState(this);
	private View progressBar;

	public Lane getLane(final int laneIndex)
	{
		return (Lane) findViewById(getResources().getIdentifier(
				"lane" + (laneIndex + 1), "id", getPackageName()));
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		// Set up the progress bar area
		progressBar = getLayoutInflater().inflate(R.layout.progress_bar, null);
		final ActionBar bar = getActionBar();
		bar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
				ActionBar.DISPLAY_SHOW_CUSTOM);
		final ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		layoutParams.gravity = Gravity.LEFT;
		bar.setCustomView(progressBar, layoutParams);
		// Set up game listeners
		final ImageView stockView = (ImageView) findViewById(R.id.stock);
		stockView.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				Log.d(TAG, "Clicked stock");
				gameState.clickStock();
			}
		});
		final ImageView wasteTopView = (ImageView) findViewById(R.id.waste1);
		wasteTopView.setOnTouchListener(new OnTouchListener()
		{
			@Override
			public boolean onTouch(final View v, final MotionEvent event)
			{
				if (event.getAction() != MotionEvent.ACTION_DOWN
						|| gameState.isWasteEmpty())
					return false;
				Log.d(TAG, "W: Starting drag at waste");
				final ClipData dragData = ClipData.newPlainText(
						gameState.getWasteTop(), gameState.getWasteTop());
				v.startDrag(dragData, new View.DragShadowBuilder(v), 0, 0);
				return true;
			}
		});
		wasteTopView.setOnDragListener(new OnDragListener()
		{
			@Override
			public boolean onDrag(final View v, final DragEvent event)
			{
				final boolean fromMe = (Integer) event.getLocalState() == 0;
				if (!fromMe)
					return false;
				if (event.getAction() == DragEvent.ACTION_DRAG_STARTED)
				{
					final String card = event.getClipDescription().getLabel()
							.toString();
					Log.d(TAG, "W: Drag started of mine of " + card + ": "
							+ event);
				}
				else if (event.getAction() == DragEvent.ACTION_DRAG_ENDED)
				{
					Log.d(TAG, "W: Drag ended of mine: " + event.getResult());
					if (!event.getResult())
						gameState.attemptAutoMoveFromWasteToFoundation();
				}
				return false;
			}
		});
		for (int curFoundation = 0; curFoundation < 12; curFoundation++)
		{
			final int foundationId = getResources().getIdentifier(
					"foundation" + (curFoundation + 1), "id", getPackageName());
			final ImageView foundationLayout = (ImageView) findViewById(foundationId);
			foundationLayout.setOnTouchListener(new OnFoundationTouchListener(
					curFoundation + 1));
			foundationLayout.setOnDragListener(new OnFoundationDragListener(
					curFoundation + 1));
		}
		for (int curLane = 0; curLane < 13; curLane++)
		{
			final int laneId = getResources().getIdentifier(
					"lane" + (curLane + 1), "id", getPackageName());
			final Lane laneLayout = (Lane) findViewById(laneId);
			laneLayout.setOnCardFlipListener(new OnCardFlipListener(curLane));
			laneLayout.setLaneId(curLane + 1);
			laneLayout.setGameState(gameState);
		}
		if (savedInstanceState == null)
			startGame();
	}

	@Override
	protected Dialog onCreateDialog(final int dialogId)
	{
		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		switch (dialogId)
		{
			case DIALOG_ID_WINNING:
				final TextView timeView = (TextView) progressBar
						.findViewById(R.id.time);
				final CharSequence time = timeView.getText();
				final TextView moveCountView = (TextView) progressBar
						.findViewById(R.id.move_count);
				final CharSequence moveCount = moveCountView.getText();
				final String message = getResources().getString(
						R.string.win_dialog_1)
						+ time
						+ getResources().getString(R.string.win_dialog_1)
						+ moveCount
						+ getResources().getString(R.string.win_dialog_1);
				builder.setMessage(message)
						.setCancelable(false)
						.setPositiveButton(
								getResources().getString(R.string.new_game),
								new DialogInterface.OnClickListener()
								{
									@Override
									public void onClick(
											final DialogInterface dialog,
											final int id)
									{
										dialog.cancel();
										gameState.newGame();
									}
								})
						.setNegativeButton(
								getResources().getString(R.string.exit),
								new DialogInterface.OnClickListener()
								{
									@Override
									public void onClick(
											final DialogInterface dialog,
											final int id)
									{
										TripleSolitaireActivity.this.finish();
									}
								});
				return builder.create();
			case DIALOG_ID_SHOW_GAME_ID:
				builder.setMessage(
						getResources().getString(R.string.game_id_label)
								+ gameState.getGameId())
						.setCancelable(false)
						.setPositiveButton("OK",
								new DialogInterface.OnClickListener()
								{
									@Override
									public void onClick(
											final DialogInterface dialog,
											final int id)
									{
										dialog.cancel();
									}
								});
				return builder.create();
		}
		return null;
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		getMenuInflater().inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		// Handle item selection
		switch (item.getItemId())
		{
			case R.id.new_game:
				startGame();
				return true;
			case R.id.game_id:
				showDialog(DIALOG_ID_SHOW_GAME_ID);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onRestoreInstanceState(final Bundle savedInstanceState)
	{
		super.onRestoreInstanceState(savedInstanceState);
		gameState.onRestoreInstanceState(savedInstanceState);
	}

	@Override
	public void onSaveInstanceState(final Bundle outState)
	{
		super.onSaveInstanceState(outState);
		gameState.onSaveInstanceState(outState);
	}

	private void startGame()
	{
		Log.d(TAG, "Starting new game...");
		gameState.newGame();
	}

	public void updateFoundationUI(final int foundationIndex)
	{
		final String foundationCard = gameState
				.getFoundationCard(foundationIndex);
		final int foundationViewId = getResources().getIdentifier(
				"foundation" + (foundationIndex + 1), "id", getPackageName());
		final ImageView foundationView = (ImageView) findViewById(foundationViewId);
		if (foundationCard == null)
		{
			foundationView.setBackgroundResource(R.drawable.foundation);
			foundationView.setOnTouchListener(null);
		}
		else
		{
			foundationView.setBackgroundResource(getResources().getIdentifier(
					foundationCard, "drawable", getPackageName()));
			foundationView.setOnTouchListener(new OnFoundationTouchListener(
					foundationIndex + 1));
		}
	}

	public void updateGameId(final long gameId)
	{
		final TextView gameIdView = (TextView) progressBar
				.findViewById(R.id.game_id);
		gameIdView.setText(Long.toString(gameId));
	}

	public void updateMoveCount(final int moveCount)
	{
		final TextView moveCountView = (TextView) progressBar
				.findViewById(R.id.move_count);
		moveCountView.setText(Integer.toString(moveCount));
	}

	public void updateStockUI()
	{
		final ImageView stockView = (ImageView) findViewById(R.id.stock);
		if (gameState.isStockEmpty())
			stockView.setBackgroundResource(R.drawable.lane);
		else
			stockView.setBackgroundResource(R.drawable.back);
	}

	public void updateTime(final int timeInSeconds)
	{
		final int minutes = timeInSeconds / 60;
		final int seconds = timeInSeconds % 60;
		final TextView timeView = (TextView) progressBar
				.findViewById(R.id.time);
		final StringBuilder sb = new StringBuilder();
		sb.append(minutes);
		sb.append(':');
		if (seconds < 10)
			sb.append(0);
		sb.append(seconds);
		timeView.setText(sb);
	}

	public void updateWasteUI()
	{
		for (int wasteIndex = 0; wasteIndex < 3; wasteIndex++)
			updateWasteUI(wasteIndex);
	}

	private void updateWasteUI(final int wasteIndex)
	{
		final String wasteCard = gameState.getWasteCard(wasteIndex);
		final ImageView waste = (ImageView) findViewById(getResources()
				.getIdentifier("waste" + (wasteIndex + 1), "id",
						getPackageName()));
		if (wasteCard == null)
			waste.setBackgroundResource(0);
		else
			waste.setBackgroundResource(getResources().getIdentifier(wasteCard,
					"drawable", getPackageName()));
	}
}