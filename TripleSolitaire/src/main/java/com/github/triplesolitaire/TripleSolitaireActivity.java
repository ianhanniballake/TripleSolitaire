package com.github.triplesolitaire;

import java.util.ArrayList;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ClipData;
import android.content.ContentProviderOperation;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.Loader;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.github.triplesolitaire.Move.Type;
import com.github.triplesolitaire.provider.GameContract;
import com.google.android.gms.appstate.AppStateClient;
import com.google.android.gms.appstate.OnStateLoadedListener;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.games.GamesClient;
import com.google.android.gms.games.achievement.Achievement;
import com.google.android.gms.games.achievement.AchievementBuffer;
import com.google.android.gms.games.achievement.OnAchievementsLoadedListener;
import com.google.example.games.basegameutils.BaseGameActivity;

/**
 * Main class which controls the UI of the Triple Solitaire game
 */
public class TripleSolitaireActivity extends BaseGameActivity implements LoaderCallbacks<Cursor>,
		OnStateLoadedListener, OnAchievementsLoadedListener
{
	/**
	 * Handles Card flip clicks
	 */
	private class OnCardFlipListener implements OnClickListener
	{
		/**
		 * One-based index (1 through 13) of the lane clicked
		 */
		private final int laneIndex;

		/**
		 * Creates a new listener for the given lane
		 * 
		 * @param laneIndex
		 *            One-based index (1 through 13) of the lane to be clicked
		 */
		public OnCardFlipListener(final int laneIndex)
		{
			this.laneIndex = laneIndex;
		}

		/**
		 * Triggers flipping over a card in this lane
		 * 
		 * @see android.view.View.OnClickListener#onClick(android.view.View)
		 */
		@Override
		public void onClick(final View v)
		{
			gameState.move(new Move(Move.Type.FLIP, laneIndex));
		}
	}

	/**
	 * Responds to dragging/dropping events on the foundation
	 */
	private class OnFoundationDragListener implements OnDragListener
	{
		/**
		 * Negative One-based index (-1 through -12) for the foundation index
		 */
		private final int foundationIndex;

		/**
		 * Creates a new drag listener for the given foundation
		 * 
		 * @param foundationIndex
		 *            Negative One-based index (-1 through -12)
		 */
		public OnFoundationDragListener(final int foundationIndex)
		{
			this.foundationIndex = foundationIndex;
		}

		/**
		 * Responds to drag events on the foundation
		 * 
		 * @see android.view.View.OnDragListener#onDrag(android.view.View, android.view.DragEvent)
		 */
		@Override
		public boolean onDrag(final View v, final DragEvent event)
		{
			final boolean isMyFoundation = foundationIndex == (Integer) event.getLocalState();
			if (event.getAction() == DragEvent.ACTION_DRAG_STARTED)
			{
				final String foundationCard = gameState.getFoundationCard(foundationIndex);
				if (isMyFoundation)
				{
					if (BuildConfig.DEBUG)
						Log.d(TripleSolitaireActivity.TAG, "Drag " + foundationIndex + ": Started of " + foundationCard);
					return false;
				}
				final String card = event.getClipDescription().getLabel().toString();
				return gameState.acceptFoundationDrop(foundationIndex, card);
			}
			else if (event.getAction() == DragEvent.ACTION_DROP)
			{
				System.gc();
				final String card = event.getClipData().getItemAt(0).getText().toString();
				final int from = (Integer) event.getLocalState();
				gameState.move(new Move(Type.PLAYER_MOVE, foundationIndex, from, card));
				return true;
			}
			return true;
		}
	}

	/**
	 * Touch listener used to start a drag event from a foundation
	 */
	private class OnFoundationTouchListener implements OnTouchListener
	{
		/**
		 * Negative One-based index (-1 through -12)
		 */
		private final int foundationIndex;

		/**
		 * Creates a new listener for the given foundation
		 * 
		 * @param foundationIndex
		 *            Negative One-based index (-1 through -12)
		 */
		public OnFoundationTouchListener(final int foundationIndex)
		{
			this.foundationIndex = foundationIndex;
		}

		/**
		 * Responds to ACTION_DOWN events to start drags from the foundation
		 * 
		 * @see android.view.View.OnTouchListener#onTouch(android.view.View, android.view.MotionEvent)
		 */
		@Override
		public boolean onTouch(final View v, final MotionEvent event)
		{
			final String foundationCard = gameState.getFoundationCard(foundationIndex);
			if (event.getAction() != MotionEvent.ACTION_DOWN || foundationCard == null)
				return false;
			final ClipData dragData = ClipData.newPlainText(foundationCard, foundationCard);
			return v.startDrag(dragData, new View.DragShadowBuilder(v), foundationIndex, 0);
		}
	}

	private static final int OUR_STATE_KEY = 0;
	private static final int REQUEST_ACHIEVEMENTS = 0;
	private static final int REQUEST_LEADERBOARDS = 1;
	/**
	 * Logging tag
	 */
	private static final String TAG = "TripleSolitaireActivity";
	/**
	 * Game state which saves and manages the current game
	 */
	GameState gameState;
	/**
	 * Handler used to post delayed calls
	 */
	final Handler handler = new Handler();
	private boolean mAlreadyLoadedState = false;
	private boolean mPendingUpdateState = false;
	private StatsState stats = new StatsState();

	/**
	 * Constructs a new TripleSolitaireActivity that uses all Google Play Services
	 */
	public TripleSolitaireActivity()
	{
		// request that superclass initialize and manage the Google Play Services for us
		super(BaseGameActivity.CLIENT_ALL);
	}

	/**
	 * Animates the given move by creating a copy of the source view and animating it over to the final position before
	 * hiding the temporary view and showing the final destination
	 * 
	 * @param move
	 *            Move to animate
	 */
	public void animate(final Move move)
	{
		final Point fromLoc;
		if (move.getFromIndex() < 0)
			fromLoc = getFoundationLoc(-1 * move.getFromIndex() - 1);
		else if (move.getFromIndex() == 0)
			fromLoc = getWasteLoc();
		else
			fromLoc = getCascadeLoc(move.getFromIndex() - 1);
		final Point toLoc;
		if (move.getToIndex() < 0)
			toLoc = getFoundationLoc(-1 * move.getToIndex() - 1);
		else if (move.getToIndex() == 0)
			toLoc = getWasteLoc();
		else
			toLoc = getCascadeLoc(move.getToIndex() - 1);
		final Card toAnimate = new Card(getBaseContext(), getResources().getIdentifier(move.getCard(), "drawable",
				getPackageName()));
		final FrameLayout layout = (FrameLayout) findViewById(R.id.animateLayout);
		layout.addView(toAnimate);
		layout.setX(fromLoc.x);
		layout.setY(fromLoc.y);
		layout.setVisibility(View.VISIBLE);
		final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		final String animationSpeedPreference;
		if (move.getType() == Move.Type.UNDO)
			animationSpeedPreference = preferences.getString(Preferences.ANIMATE_SPEED_UNDO_PREFERENCE_KEY,
					getString(R.string.pref_animation_speed_undo_default));
		else
			animationSpeedPreference = preferences.getString(Preferences.ANIMATE_SPEED_AUTO_PLAY_PREFERENCE_KEY,
					getString(R.string.pref_animation_speed_auto_play_default));
		try
		{
			layout.animate().x(toLoc.x).y(toLoc.y).setDuration(Integer.valueOf(animationSpeedPreference))
					.setListener(new AnimatorListenerAdapter()
					{
						@Override
						public void onAnimationEnd(final Animator animation)
						{
							if (move.getToIndex() < 0)
								updateFoundationUI(-1 * move.getToIndex() - 1);
							else if (move.getToIndex() == 0)
								updateWasteUI();
							else
								getLane(move.getToIndex() - 1).addCascade(move.getCascade());
							layout.removeAllViews();
							layout.setVisibility(View.GONE);
							if (move.getType() != Move.Type.UNDO)
								gameState.animationCompleted();
						}
					});
		} catch (final NullPointerException e)
		{
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
	private void cancelAnimation()
	{
		final FrameLayout layout = (FrameLayout) findViewById(R.id.animateLayout);
		layout.animate().cancel();
	}

	/**
	 * Gets the screen location for the top cascade card of the given lane
	 * 
	 * @param laneIndex
	 *            One-based index (1 through 13) for the lane
	 * @return The exact (x,y) position of the top cascade card in the lane
	 */
	private Point getCascadeLoc(final int laneIndex)
	{
		final RelativeLayout lane = (RelativeLayout) findViewById(R.id.lane);
		final Card cascadeView = getLane(laneIndex).getTopCascadeCard();
		final float x = cascadeView.getX() + cascadeView.getPaddingLeft() + getLane(laneIndex).getX()
				+ getLane(laneIndex).getPaddingLeft() + lane.getX() + lane.getPaddingLeft();
		final float y = cascadeView.getY() + cascadeView.getPaddingTop() + getLane(laneIndex).getY()
				+ getLane(laneIndex).getPaddingTop() + lane.getY() + lane.getPaddingTop();
		return new Point((int) x, (int) y);
	}

	/**
	 * Gets the screen location for the given foundation
	 * 
	 * @param foundationIndex
	 *            Negative One-based index (-1 through -12)
	 * @return The exact (x,y) position of the foundation
	 */
	private Point getFoundationLoc(final int foundationIndex)
	{
		final RelativeLayout foundationLayout = (RelativeLayout) findViewById(R.id.foundation);
		final ImageView foundationView = (ImageView) findViewById(getResources().getIdentifier(
				"foundation" + (foundationIndex + 1), "id", getPackageName()));
		final float x = foundationView.getX() + foundationView.getPaddingLeft() + foundationLayout.getX()
				+ foundationLayout.getPaddingLeft();
		final float y = foundationView.getY() + foundationView.getPaddingTop() + foundationLayout.getY()
				+ foundationLayout.getPaddingTop();
		return new Point((int) x, (int) y);
	}

	/**
	 * Gets a string representation (MMM:SS) of the current game time
	 * 
	 * @return Current formatted game time
	 */
	public CharSequence getGameTime()
	{
		final int timeInSeconds = gameState.getTimeInSeconds();
		final int minutes = timeInSeconds / 60;
		final int seconds = timeInSeconds % 60;
		final StringBuilder sb = new StringBuilder();
		sb.append(minutes);
		sb.append(':');
		if (seconds < 10)
			sb.append(0);
		sb.append(seconds);
		return sb;
	}

	/**
	 * Gets the Lane associated with the given index
	 * 
	 * @param laneIndex
	 *            One-based index (1 through 13) for the lane
	 * @return The Lane for the given index
	 */
	public Lane getLane(final int laneIndex)
	{
		return (Lane) findViewById(getResources().getIdentifier("lane" + (laneIndex + 1), "id", getPackageName()));
	}

	/**
	 * Gets the current move count
	 * 
	 * @return Current move count
	 */
	public int getMoveCount()
	{
		return gameState.getMoveCount();
	}

	/**
	 * Gets the screen location for the top card in the waste
	 * 
	 * @return The exact (x,y) position of the top card in the waste
	 */
	private Point getWasteLoc()
	{
		final RelativeLayout waste = (RelativeLayout) findViewById(R.id.waste);
		final ImageView waste1View = (ImageView) findViewById(R.id.waste1);
		final float x = waste.getX() + waste.getPaddingLeft() + waste1View.getX() + waste1View.getPaddingLeft();
		final float y = waste.getY() + waste.getPaddingTop() + waste1View.getY() + waste1View.getPaddingTop();
		return new Point((int) x, (int) y);
	}

	/**
	 * Starts a new game, resetting the game state and UI to match
	 */
	public void newGame()
	{
		gameState.newGame();
	}

	@Override
	public void onAchievementsLoaded(final int statusCode, final AchievementBuffer buffer)
	{
		switch (statusCode)
		{
			case GamesClient.STATUS_OK:
				if (BuildConfig.DEBUG)
					Log.d(TripleSolitaireActivity.TAG, "Achievements loaded successfully");
				// Data was successfully loaded from the cloud, update incremental achievements
				final String win10 = getString(R.string.achievement_win_10);
				final String win100 = getString(R.string.achievement_win_100);
				final String win250 = getString(R.string.achievement_win_250);
				for (final Achievement achievement : buffer)
				{
					final String achievementId = achievement.getAchievementId();
					if (achievement.getType() != Achievement.TYPE_INCREMENTAL)
						continue;
					final int increment = stats.getGamesWon() - achievement.getCurrentSteps();
					if (achievementId.equals(win10) && increment > 0)
						getGamesClient().incrementAchievement(win10, increment);
					if (achievementId.equals(win100) && increment > 0)
						getGamesClient().incrementAchievement(win10, increment);
					if (achievementId.equals(win250) && increment > 0)
						getGamesClient().incrementAchievement(win10, increment);
				}
				break;
			case GamesClient.STATUS_NETWORK_ERROR_NO_DATA:
				if (BuildConfig.DEBUG)
					Log.d(TripleSolitaireActivity.TAG, "Achievements not loaded - network error with no data");
				// can't reach cloud, and we have no local state.
				// TODO warn about network error
				break;
			case GamesClient.STATUS_NETWORK_ERROR_STALE_DATA:
				if (BuildConfig.DEBUG)
					Log.d(TripleSolitaireActivity.TAG, "Achievements not loaded - network error with stale data");
				// TODO warn about stale data
				break;
			case GamesClient.STATUS_CLIENT_RECONNECT_REQUIRED:
				if (BuildConfig.DEBUG)
					Log.d(TripleSolitaireActivity.TAG, "Achievements not loaded - reconnect required");
				// need to reconnect AppStateClient
				reconnectClients(BaseGameActivity.CLIENT_GAMES);
				break;
			default:
				// TODO warn about generic error
				break;
		}
	}

	@Override
	public void onBackPressed()
	{
		if (!gameState.gameInProgress)
		{
			super.onBackPressed();
			return;
		}
		final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_alert).setTitle(R.string.quit_title)
				.setMessage(R.string.quit_message)
				.setPositiveButton(R.string.quit_positive, new DialogInterface.OnClickListener()
				{
					@SuppressWarnings("synthetic-access")
					@Override
					public void onClick(final DialogInterface dialog, final int which)
					{
						TripleSolitaireActivity.super.onBackPressed();
					}
				}).setNegativeButton(R.string.quit_negative, null);
		dialogBuilder.setOnCancelListener(new OnCancelListener()
		{
			@SuppressWarnings("synthetic-access")
			@Override
			public void onCancel(final DialogInterface dialog)
			{
				TripleSolitaireActivity.super.onBackPressed();
			}
		});
		dialogBuilder.show();
	}

	/**
	 * Called when the activity is first created. Sets up the appropriate listeners and starts a new game
	 * 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		gameState = new GameState(this);
		setContentView(R.layout.main);
		// Set up the progress bar area
		final View progressBar = getLayoutInflater().inflate(R.layout.progress_bar, null);
		final ActionBar bar = getActionBar();
        bar.setDisplayHomeAsUpEnabled(true);
		bar.setDisplayShowCustomEnabled(true);
		final ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(LayoutParams.WRAP_CONTENT,
				LayoutParams.WRAP_CONTENT);
		layoutParams.gravity = Gravity.LEFT;
		bar.setCustomView(progressBar, layoutParams);
		// Set up sign in button
		final SignInButton signIn = (SignInButton) findViewById(R.id.sign_in);
		signIn.setOnClickListener(new OnClickListener()
		{
			@SuppressWarnings("synthetic-access")
			@Override
			public void onClick(final View v)
			{
				beginUserInitiatedSignIn();
			}
		});
		// Set up game listeners
		final ImageView stockView = (ImageView) findViewById(R.id.stock);
		stockView.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				if (!gameState.isStockEmpty() || !gameState.isWasteEmpty())
					gameState.move(new Move(Move.Type.STOCK));
			}
		});
		final ImageView wasteTopView = (ImageView) findViewById(R.id.waste1);
		wasteTopView.setOnTouchListener(new OnTouchListener()
		{
			@Override
			public boolean onTouch(final View v, final MotionEvent event)
			{
				if (event.getAction() != MotionEvent.ACTION_DOWN || gameState.isWasteEmpty())
					return false;
				final ClipData dragData = ClipData.newPlainText(gameState.getWasteCard(0), gameState.getWasteCard(0));
				return v.startDrag(dragData, new View.DragShadowBuilder(v), 0, 0);
			}
		});
		wasteTopView.setOnDragListener(new OnDragListener()
		{
			@Override
			public boolean onDrag(final View v, final DragEvent event)
			{
				final boolean fromMe = (Integer) event.getLocalState() == 0;
				if (event.getAction() == DragEvent.ACTION_DRAG_STARTED && fromMe)
				{
					if (BuildConfig.DEBUG)
					{
						final String card = event.getClipDescription().getLabel().toString();
						Log.d(TripleSolitaireActivity.TAG, "Drag W: Started of " + card);
					}
					return true;
				}
				else if (event.getAction() == DragEvent.ACTION_DRAG_ENDED && !event.getResult() && fromMe)
				{
					System.gc();
					handler.post(new Runnable()
					{
						@Override
						public void run()
						{
							gameState.attemptAutoMoveFromWasteToFoundation();
						}
					});
					return true;
				}
				return false;
			}
		});
		for (int curFoundation = 0; curFoundation < 12; curFoundation++)
		{
			final int foundationId = getResources().getIdentifier("foundation" + (curFoundation + 1), "id",
					getPackageName());
			final ImageView foundationLayout = (ImageView) findViewById(foundationId);
			foundationLayout.setOnTouchListener(new OnFoundationTouchListener(-1 * (curFoundation + 1)));
			foundationLayout.setOnDragListener(new OnFoundationDragListener(-1 * (curFoundation + 1)));
		}
		for (int curLane = 0; curLane < 13; curLane++)
		{
			final int laneId = getResources().getIdentifier("lane" + (curLane + 1), "id", getPackageName());
			final Lane laneLayout = (Lane) findViewById(laneId);
			laneLayout.setOnCardFlipListener(new OnCardFlipListener(curLane + 1));
			laneLayout.setLaneId(curLane + 1);
			laneLayout.setGameState(gameState);
		}
		if (savedInstanceState == null)
			gameState.newGame();
		getLoaderManager().initLoader(0, null, this);
	}

	@Override
	public Loader<Cursor> onCreateLoader(final int id, final Bundle args)
	{
		return new CursorLoader(this, GameContract.Games.CONTENT_URI, null, null, null, null);
	}

	/**
	 * One time method to inflate the options menu / action bar
	 * 
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		getMenuInflater().inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public void onLoaderReset(final Loader<Cursor> loader)
	{
		// Nothing to do
	}

	@Override
	public void onLoadFinished(final Loader<Cursor> loader, final Cursor data)
	{
		stats = stats.unionWith(new StatsState(data));
		if (isSignedIn())
			saveToCloud();
		else if (data.getCount() > 0)
			mPendingUpdateState = true;
	}

	/**
	 * Called to handle when options menu / action bar buttons are tapped
	 * 
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.undo:
				gameState.undo();
				return true;
			case R.id.new_game:
				gameState.newGame();
				return true;
			case R.id.stats:
				StatsDialogFragment.createInstance(stats).show(getFragmentManager(), "stats");
				return true;
			case R.id.achievements:
				startActivityForResult(getGamesClient().getAchievementsIntent(), REQUEST_ACHIEVEMENTS);
				return true;
			case R.id.leaderboards:
				startActivityForResult(getGamesClient().getAllLeaderboardsIntent(), REQUEST_LEADERBOARDS);
				return true;
			case R.id.settings:
				startActivity(new Intent(this, Preferences.class));
				return true;
			case R.id.sign_out:
				signOut();
				findViewById(R.id.sign_in_layout).setVisibility(View.VISIBLE);
				return true;
			case android.R.id.home:
			case R.id.about:
				final AboutDialogFragment aboutDialogFragment = new AboutDialogFragment();
				aboutDialogFragment.show(getFragmentManager(), "about");
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
			cancelAnimation();
	}

	/**
	 * Method called every time the options menu is invalidated/repainted. Enables/disables the undo button
	 * 
	 * @see android.app.Activity#onPrepareOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onPrepareOptionsMenu(final Menu menu)
	{
		super.onPrepareOptionsMenu(menu);
		menu.findItem(R.id.undo).setEnabled(gameState.canUndo());
		final boolean isSignedIn = isSignedIn();
		menu.findItem(R.id.achievements).setVisible(isSignedIn);
		menu.findItem(R.id.leaderboards).setVisible(isSignedIn);
		menu.findItem(R.id.sign_out).setVisible(isSignedIn);
		return true;
	}

	/**
	 * Restores the game state
	 * 
	 * @see android.app.Activity#onRestoreInstanceState(android.os.Bundle)
	 */
	@Override
	protected void onRestoreInstanceState(final Bundle savedInstanceState)
	{
		gameState.onRestoreInstanceState(savedInstanceState);
		super.onRestoreInstanceState(savedInstanceState);
	}

	/**
	 * Saves the game state
	 * 
	 * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
	 */
	@Override
	protected void onSaveInstanceState(final Bundle outState)
	{
		super.onSaveInstanceState(outState);
		gameState.onSaveInstanceState(outState);
		if (BuildConfig.DEBUG)
			Log.d(TripleSolitaireActivity.TAG, "onSaveInstanceState");
	}

	@Override
	public void onSignInFailed()
	{
		if (BuildConfig.DEBUG)
			Log.d(TripleSolitaireActivity.TAG, "onSignInFailed");
		invalidateOptionsMenu();
	}

	@Override
	public void onSignInSucceeded()
	{
		if (BuildConfig.DEBUG)
			Log.d(TripleSolitaireActivity.TAG, "onSignInSucceeded");
		invalidateOptionsMenu();
		findViewById(R.id.sign_in_layout).setVisibility(View.GONE);
		if (!mAlreadyLoadedState)
			getAppStateClient().loadState(this, OUR_STATE_KEY);
	}

	@Override
	public void onStateConflict(final int stateKey, final String resolvedVersion, final byte[] localData,
			final byte[] serverData)
	{
		if (BuildConfig.DEBUG)
			Log.d(TripleSolitaireActivity.TAG, "onStateConflict");
		// Union the two sets of data together to form a resolved, consistent set of stats
		final StatsState localStats = new StatsState(localData);
		final StatsState serverStats = new StatsState(serverData);
		final StatsState resolvedGame = localStats.unionWith(serverStats);
		getAppStateClient().resolveState(this, OUR_STATE_KEY, resolvedVersion, resolvedGame.toBytes());
	}

	@Override
	public void onStateLoaded(final int statusCode, final int stateKey, final byte[] localData)
	{
		switch (statusCode)
		{
			case AppStateClient.STATUS_OK:
				if (BuildConfig.DEBUG)
					Log.d(TripleSolitaireActivity.TAG, "State loaded successfully");
				// Data was successfully loaded from the cloud: merge with local data.
				stats = stats.unionWith(new StatsState(localData));
				mAlreadyLoadedState = true;
				persistStats();
				if (mPendingUpdateState)
					saveToCloud();
				break;
			case AppStateClient.STATUS_STATE_KEY_NOT_FOUND:
				if (BuildConfig.DEBUG)
					Log.d(TripleSolitaireActivity.TAG, "State loaded, no key found");
				// key not found means there is no saved data. To us, this is the same as
				// having empty data, so we treat this as a success.
				mAlreadyLoadedState = true;
				if (mPendingUpdateState)
					saveToCloud();
				break;
			case AppStateClient.STATUS_NETWORK_ERROR_NO_DATA:
				if (BuildConfig.DEBUG)
					Log.d(TripleSolitaireActivity.TAG, "State not loaded - network error with no data");
				// can't reach cloud, and we have no local state. Warn user that
				// they may not see their existing progress, but any new progress won't be lost.
				// TODO warn about network error
				break;
			case AppStateClient.STATUS_NETWORK_ERROR_STALE_DATA:
				if (BuildConfig.DEBUG)
					Log.d(TripleSolitaireActivity.TAG, "State not loaded - network error with stale data");
				// can't reach cloud, but we have locally cached data.
				// TODO warn about stale data
				break;
			case AppStateClient.STATUS_CLIENT_RECONNECT_REQUIRED:
				if (BuildConfig.DEBUG)
					Log.d(TripleSolitaireActivity.TAG, "State not loaded - reconnect required");
				// need to reconnect AppStateClient
				reconnectClients(BaseGameActivity.CLIENT_APPSTATE);
				break;
			default:
				// TODO warn about generic error
				break;
		}
	}

	/**
	 * Pauses/resumes the game timer when window focus is lost/gained, respectively
	 * 
	 * @see android.app.Activity#onWindowFocusChanged(boolean)
	 */
	@Override
	public void onWindowFocusChanged(final boolean hasFocus)
	{
		if (hasFocus)
			gameState.resumeGame();
		else
			gameState.pauseGame();
	}

	private void persistStats()
	{
		final ArrayList<ContentProviderOperation> operations = stats.getLocalSaveOperations();
		try
		{
			getContentResolver().applyBatch(GameContract.AUTHORITY, operations);
		} catch (final RemoteException e)
		{
			Log.e(StatsState.class.getSimpleName(), "Failed persisting stats", e);
		} catch (final OperationApplicationException e)
		{
			Log.e(StatsState.class.getSimpleName(), "Failed persisting stats", e);
		}
	}

	private void saveToCloud()
	{
		getAppStateClient().updateState(OUR_STATE_KEY, stats.toBytes());
		final GamesClient gamesClient = getGamesClient();
		// Check win streak achievements
		final int longestWinStreak = stats.getLongestWinStreak();
		if (BuildConfig.DEBUG)
			Log.d(TripleSolitaireActivity.TAG, "Longest Win Streak: " + longestWinStreak);
		if (longestWinStreak > 0)
			gamesClient.unlockAchievement(getString(R.string.achievement_winner));
		if (longestWinStreak > 5)
			gamesClient.unlockAchievement(getString(R.string.achievement_streak_5));
		if (longestWinStreak > 10)
			gamesClient.unlockAchievement(getString(R.string.achievement_streak_10));
		// Check minimum moves achievements
		final int minimumMoves = stats.getMinimumMoves();
		if (BuildConfig.DEBUG)
			Log.d(TripleSolitaireActivity.TAG, "Minimum Moves: " + minimumMoves);
		gamesClient.submitScore(getString(R.string.leaderboard_moves), minimumMoves);
		if (minimumMoves < 400)
			gamesClient.unlockAchievement(getString(R.string.achievement_moves_400));
		if (minimumMoves < 300)
			gamesClient.unlockAchievement(getString(R.string.achievement_moves_300));
		// Check shortest time achievements
		final int shortestTime = stats.getShortestTime();
		if (BuildConfig.DEBUG)
			Log.d(TripleSolitaireActivity.TAG, "Shortest Time (minutes): " + (double) shortestTime / 60);
		gamesClient.submitScore(getString(R.string.leaderboard_time), shortestTime * DateUtils.SECOND_IN_MILLIS);
		if (shortestTime < 15 * 60)
			gamesClient.unlockAchievement(getString(R.string.achievement_time_15));
		if (shortestTime < 10 * 60)
			gamesClient.unlockAchievement(getString(R.string.achievement_time_10));
		if (shortestTime < 8 * 60)
			gamesClient.unlockAchievement(getString(R.string.achievement_time_8));
		// Load achievements to handle incremental achievements
		gamesClient.loadAchievements(this);
		mPendingUpdateState = false;
	}

	/**
	 * Updates the given foundation UI
	 * 
	 * @param foundationIndex
	 *            Negative One-based index (-1 through -12) for the foundation
	 */
	public void updateFoundationUI(final int foundationIndex)
	{
		final String foundationCard = gameState.getFoundationCard(-1 * (foundationIndex + 1));
		final int foundationViewId = getResources().getIdentifier("foundation" + (foundationIndex + 1), "id",
				getPackageName());
		final ImageView foundationView = (ImageView) findViewById(foundationViewId);
		if (foundationCard == null)
		{
			foundationView.setBackgroundResource(R.drawable.foundation);
			foundationView.setOnTouchListener(null);
		}
		else
		{
			foundationView.setBackgroundResource(getResources().getIdentifier(foundationCard, "drawable",
					getPackageName()));
			foundationView.setOnTouchListener(new OnFoundationTouchListener(-1 * (foundationIndex + 1)));
		}
	}

	/**
	 * Updates the move count UI
	 */
	public void updateMoveCount()
	{
		final TextView moveCountView = (TextView) getActionBar().getCustomView().findViewById(R.id.move_count);
		moveCountView.setText(Integer.toString(getMoveCount()));
	}

	/**
	 * Updates the stock UI
	 */
	public void updateStockUI()
	{
		final ImageView stockView = (ImageView) findViewById(R.id.stock);
		if (gameState.isStockEmpty())
			stockView.setBackgroundResource(R.drawable.lane);
		else
			stockView.setBackgroundResource(R.drawable.back);
	}

	/**
	 * Updates the current game time UI
	 */
	public void updateTime()
	{
		final TextView timeView = (TextView) getActionBar().getCustomView().findViewById(R.id.time);
		timeView.setText(getGameTime());
	}

	/**
	 * Updates the waste UI
	 */
	public void updateWasteUI()
	{
		for (int wasteIndex = 0; wasteIndex < 3; wasteIndex++)
			updateWasteUI(wasteIndex);
	}

	/**
	 * Updates each card in the waste
	 * 
	 * @param wasteIndex
	 *            Index of the card (0-2)
	 */
	private void updateWasteUI(final int wasteIndex)
	{
		final String wasteCard = gameState.getWasteCard(wasteIndex);
		final ImageView waste = (ImageView) findViewById(getResources().getIdentifier("waste" + (wasteIndex + 1), "id",
				getPackageName()));
		if (wasteCard == null)
			waste.setBackgroundResource(0);
		else
			waste.setBackgroundResource(getResources().getIdentifier(wasteCard, "drawable", getPackageName()));
	}
}