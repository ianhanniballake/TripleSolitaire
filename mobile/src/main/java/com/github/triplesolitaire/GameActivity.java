package com.github.triplesolitaire;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * Main class which controls the UI of the Triple Solitaire game
 */
public class GameActivity extends Activity {
    /**
     * Handles Card flip clicks
     */
    private class OnCardFlipListener implements View.OnClickListener {
        /**
         * One-based index (1 through 13) of the lane clicked
         */
        private final int laneIndex;

        /**
         * Creates a new listener for the given lane
         *
         * @param laneIndex One-based index (1 through 13) of the lane to be clicked
         */
        public OnCardFlipListener(final int laneIndex) {
            this.laneIndex = laneIndex;
        }

        /**
         * Triggers flipping over a card in this lane
         *
         * @see android.view.View.OnClickListener#onClick(android.view.View)
         */
        @Override
        public void onClick(final View v) {
            gameState.move(new Move(Move.Type.FLIP, laneIndex));
        }
    }

    /**
     * Responds to dragging/dropping events on the foundation
     */
    private class OnFoundationDragListener implements View.OnDragListener {
        /**
         * Negative One-based index (-1 through -12) for the foundation index
         */
        private final int foundationIndex;

        /**
         * Creates a new drag listener for the given foundation
         *
         * @param foundationIndex Negative One-based index (-1 through -12)
         */
        public OnFoundationDragListener(final int foundationIndex) {
            this.foundationIndex = foundationIndex;
        }

        /**
         * Responds to drag events on the foundation
         *
         * @see android.view.View.OnDragListener#onDrag(android.view.View, android.view.DragEvent)
         */
        @Override
        public boolean onDrag(final View v, final DragEvent event) {
            final boolean isMyFoundation = foundationIndex == (Integer) event.getLocalState();
            if (event.getAction() == DragEvent.ACTION_DRAG_STARTED) {
                final String foundationCard = gameState.getFoundationCard(foundationIndex);
                if (isMyFoundation) {
                    if (BuildConfig.DEBUG)
                        Log.d(GameActivity.TAG, "Drag " + foundationIndex + ": Started of " + foundationCard);
                    return false;
                }
                final String card = event.getClipDescription().getLabel().toString();
                return gameState.acceptFoundationDrop(foundationIndex, card);
            } else if (event.getAction() == DragEvent.ACTION_DROP) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
                    System.gc();
                final String card = event.getClipData().getItemAt(0).getText().toString();
                final int from = (Integer) event.getLocalState();
                gameState.move(new Move(Move.Type.PLAYER_MOVE, foundationIndex, from, card));
                return true;
            }
            return true;
        }
    }

    /**
     * Touch listener used to start a drag event from a foundation
     */
    private class OnFoundationTouchListener implements View.OnTouchListener {
        /**
         * Negative One-based index (-1 through -12)
         */
        private final int foundationIndex;

        /**
         * Creates a new listener for the given foundation
         *
         * @param foundationIndex Negative One-based index (-1 through -12)
         */
        public OnFoundationTouchListener(final int foundationIndex) {
            this.foundationIndex = foundationIndex;
        }

        /**
         * Responds to ACTION_DOWN events to start drags from the foundation
         *
         * @see android.view.View.OnTouchListener#onTouch(android.view.View, android.view.MotionEvent)
         */
        @Override
        public boolean onTouch(final View v, final MotionEvent event) {
            final String foundationCard = gameState.getFoundationCard(foundationIndex);
            if (event.getAction() != MotionEvent.ACTION_DOWN || foundationCard == null)
                return false;
            final ClipData dragData = ClipData.newPlainText(foundationCard, foundationCard);
            return v.startDrag(dragData, new View.DragShadowBuilder(v), foundationIndex, 0);
        }
    }

    /**
     * Logging tag
     */
    private static final String TAG = "GameActivity";
    /**
     * Game state which saves and manages the current game
     */
    GameState gameState;
    /**
     * Handler used to post delayed calls
     */
    final Handler handler = new Handler();

    /**
     * Animates the given move by creating a copy of the source view and animating it over to the final position before
     * hiding the temporary view and showing the final destination
     *
     * @param move Move to animate
     */
    public void animate(final Move move) {
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
        final FrameLayout layout = findViewById(R.id.animateLayout);
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
        try {
            layout.animate().x(toLoc.x).y(toLoc.y).setDuration(Integer.valueOf(animationSpeedPreference))
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(final Animator animation) {
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
        } catch (final NullPointerException e) {
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
    private void cancelAnimation() {
        final FrameLayout layout = findViewById(R.id.animateLayout);
        layout.animate().cancel();
    }

    /**
     * Gets the screen location for the top cascade card of the given lane
     *
     * @param laneIndex One-based index (1 through 13) for the lane
     * @return The exact (x,y) position of the top cascade card in the lane
     */
    private Point getCascadeLoc(final int laneIndex) {
        final RelativeLayout lane = findViewById(R.id.lane);
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
     * @param foundationIndex Negative One-based index (-1 through -12)
     * @return The exact (x,y) position of the foundation
     */
    private Point getFoundationLoc(final int foundationIndex) {
        final RelativeLayout foundationLayout = findViewById(R.id.foundation);
        final ImageView foundationView = findViewById(getResources().getIdentifier(
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
    public CharSequence getGameTime() {
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
     * @param laneIndex One-based index (1 through 13) for the lane
     * @return The Lane for the given index
     */
    public Lane getLane(final int laneIndex) {
        return (Lane) findViewById(getResources().getIdentifier("lane" + (laneIndex + 1), "id", getPackageName()));
    }

    /**
     * Gets the current move count
     *
     * @return Current move count
     */
    public int getMoveCount() {
        return gameState.getMoveCount();
    }

    /**
     * Gets the screen location for the top card in the waste
     *
     * @return The exact (x,y) position of the top card in the waste
     */
    private Point getWasteLoc() {
        final RelativeLayout waste = findViewById(R.id.waste);
        final ImageView waste1View = findViewById(R.id.waste1);
        final float x = waste.getX() + waste.getPaddingLeft() + waste1View.getX() + waste1View.getPaddingLeft();
        final float y = waste.getY() + waste.getPaddingTop() + waste1View.getY() + waste1View.getPaddingTop();
        return new Point((int) x, (int) y);
    }

    @Override
    public void onBackPressed() {
        final boolean gameStarted = gameState.getTimeInSeconds() > 0;
        if (!gameStarted && !gameState.gameInProgress) {
            super.onBackPressed();
            return;
        }
        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert).setTitle(R.string.quit_title)
                .setMessage(R.string.quit_message)
                .setPositiveButton(R.string.quit_positive, new DialogInterface.OnClickListener() {
                    @SuppressWarnings("synthetic-access")
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        GameActivity.super.onBackPressed();
                    }
                }).setNegativeButton(R.string.quit_negative, null);
        dialogBuilder.show();
    }

    /**
     * Called when the activity is first created. Sets up the appropriate listeners and starts a new game
     *
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        gameState = new GameState(this);
        setContentView(R.layout.activity_game);
        // Set up the progress bar area
        final View progressBar = getLayoutInflater().inflate(R.layout.progress_bar, null);
        final ActionBar bar = getActionBar();
        bar.setDisplayHomeAsUpEnabled(true);
        bar.setDisplayShowCustomEnabled(true);
        final ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        layoutParams.gravity = Gravity.LEFT;
        bar.setCustomView(progressBar, layoutParams);
        // Set up game listeners
        final ImageView stockView = findViewById(R.id.stock);
        stockView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (!gameState.isStockEmpty() || !gameState.isWasteEmpty())
                    gameState.move(new Move(Move.Type.STOCK));
            }
        });
        final ImageView wasteTopView = findViewById(R.id.waste1);
        wasteTopView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(final View v, final MotionEvent event) {
                if (event.getAction() != MotionEvent.ACTION_DOWN || gameState.isWasteEmpty())
                    return false;
                final ClipData dragData = ClipData.newPlainText(gameState.getWasteCard(0), gameState.getWasteCard(0));
                return v.startDrag(dragData, new View.DragShadowBuilder(v), 0, 0);
            }
        });
        wasteTopView.setOnDragListener(new View.OnDragListener() {
            @Override
            public boolean onDrag(final View v, final DragEvent event) {
                final boolean fromMe = (Integer) event.getLocalState() == 0;
                if (event.getAction() == DragEvent.ACTION_DRAG_STARTED && fromMe) {
                    if (BuildConfig.DEBUG) {
                        final String card = event.getClipDescription().getLabel().toString();
                        Log.d(GameActivity.TAG, "Drag W: Started of " + card);
                    }
                    return true;
                } else if (event.getAction() == DragEvent.ACTION_DRAG_ENDED && !event.getResult() && fromMe) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
                        System.gc();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            gameState.attemptAutoMoveFromWasteToFoundation();
                        }
                    });
                    return true;
                }
                return false;
            }
        });
        for (int curFoundation = 0; curFoundation < 12; curFoundation++) {
            final int foundationId = getResources().getIdentifier("foundation" + (curFoundation + 1), "id",
                    getPackageName());
            final ImageView foundationLayout = findViewById(foundationId);
            foundationLayout.setOnTouchListener(new OnFoundationTouchListener(-1 * (curFoundation + 1)));
            foundationLayout.setOnDragListener(new OnFoundationDragListener(-1 * (curFoundation + 1)));
        }
        for (int curLane = 0; curLane < 13; curLane++) {
            final int laneId = getResources().getIdentifier("lane" + (curLane + 1), "id", getPackageName());
            final Lane laneLayout = findViewById(laneId);
            laneLayout.setOnCardFlipListener(new OnCardFlipListener(curLane + 1));
            laneLayout.setLaneId(curLane + 1);
            laneLayout.setGameState(gameState);
        }
        if (savedInstanceState == null)
            gameState.newGame();
    }

    /**
     * One time method to inflate the options menu / action bar
     *
     * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
     */
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.game, menu);
        return true;
    }

    /**
     * Called to handle when options menu / action bar buttons are tapped
     *
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.undo:
                gameState.undo();
                return true;
            case R.id.pause:
                new GamePauseDialogFragment().show(getFragmentManager(), "pause");
                return true;
            case R.id.new_game:
                gameState.newGame();
                return true;
            case R.id.settings:
                startActivity(new Intent(this, Preferences.class));
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.about:
                final AboutDialogFragment aboutDialogFragment = new AboutDialogFragment();
                aboutDialogFragment.show(getFragmentManager(), "about");
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onPause() {
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
    public boolean onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.undo).setEnabled(gameState.canUndo());
        final boolean gameStarted = gameState.getTimeInSeconds() > 0;
        final boolean gameInProgress = gameState.gameInProgress;
        menu.findItem(R.id.pause).setEnabled(gameStarted || gameInProgress);
        return true;
    }

    /**
     * Restores the game state
     *
     * @see android.app.Activity#onRestoreInstanceState(android.os.Bundle)
     */
    @Override
    protected void onRestoreInstanceState(final Bundle savedInstanceState) {
        gameState.onRestoreInstanceState(savedInstanceState);
        super.onRestoreInstanceState(savedInstanceState);
    }

    /**
     * Saves the game state
     *
     * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
     */
    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        gameState.onSaveInstanceState(outState);
        if (BuildConfig.DEBUG)
            Log.d(GameActivity.TAG, "onSaveInstanceState");
    }

    /**
     * Pauses/resumes the game timer when window focus is lost/gained, respectively
     *
     * @see android.app.Activity#onWindowFocusChanged(boolean)
     */
    @Override
    public void onWindowFocusChanged(final boolean hasFocus) {
        if (hasFocus) {
            gameState.resumeGame();
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LOW_PROFILE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else
            gameState.pauseGame();
    }

    public void triggerWin() {
        setResult(RESULT_OK, WinDialogFragment.createDataIntent(getGameTime(), getMoveCount()));
        finish();
    }

    /**
     * Updates the given foundation UI
     *
     * @param foundationIndex Negative One-based index (-1 through -12) for the foundation
     */
    public void updateFoundationUI(final int foundationIndex) {
        final String foundationCard = gameState.getFoundationCard(-1 * (foundationIndex + 1));
        final int foundationViewId = getResources().getIdentifier("foundation" + (foundationIndex + 1), "id",
                getPackageName());
        final ImageView foundationView = findViewById(foundationViewId);
        if (foundationCard == null) {
            foundationView.setBackgroundResource(R.drawable.foundation);
            foundationView.setOnTouchListener(null);
        } else {
            foundationView.setBackgroundResource(getResources().getIdentifier(foundationCard, "drawable",
                    getPackageName()));
            foundationView.setOnTouchListener(new OnFoundationTouchListener(-1 * (foundationIndex + 1)));
        }
    }

    /**
     * Updates the move count UI
     */
    public void updateMoveCount() {
        final TextView moveCountView = getActionBar().getCustomView().findViewById(R.id.move_count);
        moveCountView.setText(Integer.toString(getMoveCount()));
    }

    /**
     * Updates the stock UI
     */
    public void updateStockUI() {
        final ImageView stockView = findViewById(R.id.stock);
        if (gameState.isStockEmpty())
            stockView.setBackgroundResource(R.drawable.lane);
        else
            stockView.setBackgroundResource(R.drawable.back);
    }

    /**
     * Updates the current game time UI
     */
    public void updateTime() {
        final TextView timeView = getActionBar().getCustomView().findViewById(R.id.time);
        timeView.setText(getGameTime());
    }

    /**
     * Updates the waste UI
     */
    public void updateWasteUI() {
        for (int wasteIndex = 0; wasteIndex < 3; wasteIndex++)
            updateWasteUI(wasteIndex);
    }

    /**
     * Updates each card in the waste
     *
     * @param wasteIndex Index of the card (0-2)
     */
    private void updateWasteUI(final int wasteIndex) {
        final String wasteCard = gameState.getWasteCard(wasteIndex);
        final ImageView waste = findViewById(getResources().getIdentifier("waste" + (wasteIndex + 1), "id",
                getPackageName()));
        if (wasteCard == null)
            waste.setBackgroundResource(0);
        else
            waste.setBackgroundResource(getResources().getIdentifier(wasteCard, "drawable", getPackageName()));
    }
}