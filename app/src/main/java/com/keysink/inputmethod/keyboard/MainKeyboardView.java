/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2025 Raimondas Rimkus
 * Copyright (C) 2021 wittmane
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.keysink.inputmethod.keyboard;

import android.animation.AnimatorInflater;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.util.WeakHashMap;

import com.keysink.inputmethod.R;
import com.keysink.inputmethod.keyboard.internal.DrawingPreviewPlacerView;
import com.keysink.inputmethod.keyboard.internal.DrawingProxy;
import com.keysink.inputmethod.keyboard.internal.KeyboardIconsSet;
import com.keysink.inputmethod.keyboard.internal.KeyDrawParams;
import com.keysink.inputmethod.keyboard.internal.KeyPreviewChoreographer;
import com.keysink.inputmethod.keyboard.internal.KeyPreviewDrawParams;
import com.keysink.inputmethod.keyboard.internal.KeyPreviewView;
import com.keysink.inputmethod.keyboard.internal.MoreKeySpec;
import com.keysink.inputmethod.keyboard.internal.NonDistinctMultitouchHelper;
import com.keysink.inputmethod.keyboard.internal.TimerHandler;
import com.keysink.inputmethod.latin.Subtype;
import com.keysink.inputmethod.latin.RichInputMethodManager;
import com.keysink.inputmethod.latin.common.Constants;
import com.keysink.inputmethod.latin.common.CoordinateUtils;
import com.keysink.inputmethod.latin.utils.LanguageOnSpacebarUtils;
import com.keysink.inputmethod.latin.utils.LocaleResourceUtils;
import com.keysink.inputmethod.latin.SuggestedWords;
import com.keysink.inputmethod.latin.suggestions.SuggestionStripView;
import com.keysink.inputmethod.latin.utils.TypefaceUtils;
import com.keysink.inputmethod.latin.voice.VoiceInputState;

/**
 * A view that is responsible for detecting key presses and touch movements.
 *
 * The suggestion strip is drawn directly on this view's canvas (in the top padding area)
 * because the Boox e-ink firmware crashes when extra views are added to the IME window.
 */
public final class MainKeyboardView extends KeyboardView implements MoreKeysPanel.Controller, DrawingProxy {
    private static final String TAG = MainKeyboardView.class.getSimpleName();

    // --- Suggestion strip (canvas-drawn, no extra views) ---
    private static final float SUGGESTION_STRIP_HEIGHT_DP = 36f;
    private static final float SUGGESTION_TEXT_SIZE_SP = 16f;
    private static final float SEPARATOR_HEIGHT_DP = 1f;
    private static final float SUGGESTION_HORIZONTAL_PADDING_DP = 12f;
    // Bottom padding to prevent the last row from being clipped on Boox e-ink displays
    private static final float KEYBOARD_BOTTOM_PADDING_DP = 5f;

    private SuggestedWords mSuggestedWords = SuggestedWords.EMPTY;
    private SuggestionStripView.Listener mSuggestionListener;
    private boolean mSuggestionStripEnabled = true;
    private final int mSuggestionStripHeight;
    private final int mKeyboardBottomPadding;
    private final Paint mSuggestionPaint;
    private final Paint mSuggestionBoldPaint;
    private final Paint mSeparatorPaint;
    private final float mSuggestionHorizontalPadding;

    // --- Voice input status (drawn in suggestion strip area) ---
    private VoiceInputState mVoiceInputState = VoiceInputState.IDLE;
    private String mVoiceErrorMessage = null;
    private int mDotAnimationCounter = 0;
    private final Handler mDotAnimationHandler = new Handler(Looper.getMainLooper());
    private final Runnable mDotAnimationRunnable = new Runnable() {
        @Override
        public void run() {
            if (mVoiceInputState.getShowsAnimatedDots()) {
                mDotAnimationCounter = (mDotAnimationCounter + 1) % 3;
                invalidate(0, 0, getWidth(), mSuggestionStripHeight);
                mDotAnimationHandler.postDelayed(this, 1000);
            }
        }
    };
    private final Runnable mErrorClearRunnable = new Runnable() {
        @Override
        public void run() {
            if (mVoiceInputState == VoiceInputState.ERROR) {
                mVoiceInputState = VoiceInputState.IDLE;
                mVoiceErrorMessage = null;
                invalidate(0, 0, getWidth(), mSuggestionStripHeight);
            }
        }
    };

    /** Listener for {@link KeyboardActionListener}. */
    private KeyboardActionListener mKeyboardActionListener;

    /* Space key and its icon and background. */
    private Key mSpaceKey;
    // Stuff to draw language name on spacebar.
    private final int mLanguageOnSpacebarFinalAlpha;
    private int mLanguageOnSpacebarFormatType;
    private final float mLanguageOnSpacebarTextRatio;
    private float mLanguageOnSpacebarTextSize;
    private final int mLanguageOnSpacebarTextColor;
    // The minimum x-scale to fit the language name on spacebar.
    private static final float MINIMUM_XSCALE_OF_LANGUAGE_NAME = 0.8f;

    // Stuff to draw altCodeWhileTyping keys.
    private final ObjectAnimator mAltCodeKeyWhileTypingFadeoutAnimator;
    private final ObjectAnimator mAltCodeKeyWhileTypingFadeinAnimator;
    private int mAltCodeKeyWhileTypingAnimAlpha = Constants.Color.ALPHA_OPAQUE;

    // Drawing preview placer view
    private final DrawingPreviewPlacerView mDrawingPreviewPlacerView;
    private final int[] mOriginCoords = CoordinateUtils.newInstance();

    // Key preview
    private final KeyPreviewDrawParams mKeyPreviewDrawParams;
    private final KeyPreviewChoreographer mKeyPreviewChoreographer;

    // More keys keyboard
    private final Paint mBackgroundDimAlphaPaint = new Paint();
    private final View mMoreKeysKeyboardContainer;
    private final WeakHashMap<Key, Keyboard> mMoreKeysKeyboardCache = new WeakHashMap<>();
    private final boolean mConfigShowMoreKeysKeyboardAtTouchedPoint;
    // More keys panel (used by both more keys keyboard and more suggestions view)
    // TODO: Consider extending to support multiple more keys panels
    private MoreKeysPanel mMoreKeysPanel;

    private final KeyDetector mKeyDetector;
    private final NonDistinctMultitouchHelper mNonDistinctMultitouchHelper;

    private final TimerHandler mTimerHandler;
    private final int mLanguageOnSpacebarHorizontalMargin;

    public MainKeyboardView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.mainKeyboardViewStyle);
    }

    public MainKeyboardView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        // Suggestion strip paint setup
        final float density = context.getResources().getDisplayMetrics().density;
        mSuggestionStripHeight = (int)(SUGGESTION_STRIP_HEIGHT_DP * density);
        mKeyboardBottomPadding = (int)(KEYBOARD_BOTTOM_PADDING_DP * density);
        mSuggestionHorizontalPadding = SUGGESTION_HORIZONTAL_PADDING_DP * density;

        mSuggestionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mSuggestionPaint.setColor(Color.DKGRAY);
        mSuggestionPaint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, SUGGESTION_TEXT_SIZE_SP,
                context.getResources().getDisplayMetrics()));
        mSuggestionPaint.setTypeface(Typeface.DEFAULT);

        mSuggestionBoldPaint = new Paint(mSuggestionPaint);
        mSuggestionBoldPaint.setTypeface(Typeface.DEFAULT_BOLD);
        mSuggestionBoldPaint.setColor(Color.BLACK);

        mSeparatorPaint = new Paint();
        mSeparatorPaint.setColor(Color.LTGRAY);
        mSeparatorPaint.setStrokeWidth(SEPARATOR_HEIGHT_DP * density);

        final DrawingPreviewPlacerView drawingPreviewPlacerView =
                new DrawingPreviewPlacerView(context, attrs);

        final TypedArray mainKeyboardViewAttr = context.obtainStyledAttributes(
                attrs, R.styleable.MainKeyboardView, defStyle, R.style.MainKeyboardView);
        final int ignoreAltCodeKeyTimeout = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_ignoreAltCodeKeyTimeout, 0);
        mTimerHandler = new TimerHandler(this, ignoreAltCodeKeyTimeout);

        final float keyHysteresisDistance = mainKeyboardViewAttr.getDimension(
                R.styleable.MainKeyboardView_keyHysteresisDistance, 0.0f);
        final float keyHysteresisDistanceForSlidingModifier = mainKeyboardViewAttr.getDimension(
                R.styleable.MainKeyboardView_keyHysteresisDistanceForSlidingModifier, 0.0f);
        mKeyDetector = new KeyDetector(
                keyHysteresisDistance, keyHysteresisDistanceForSlidingModifier);

        PointerTracker.init(mainKeyboardViewAttr, mTimerHandler, this /* DrawingProxy */);

        final boolean hasDistinctMultitouch = context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT);
        mNonDistinctMultitouchHelper = hasDistinctMultitouch ? null
                : new NonDistinctMultitouchHelper();

        final int backgroundDimAlpha = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_backgroundDimAlpha, 0);
        mBackgroundDimAlphaPaint.setColor(Color.BLACK);
        mBackgroundDimAlphaPaint.setAlpha(backgroundDimAlpha);
        mLanguageOnSpacebarTextRatio = mainKeyboardViewAttr.getFraction(
                R.styleable.MainKeyboardView_languageOnSpacebarTextRatio, 1, 1, 1.0f);
        mLanguageOnSpacebarTextColor = mainKeyboardViewAttr.getColor(
                R.styleable.MainKeyboardView_languageOnSpacebarTextColor, 0);
        mLanguageOnSpacebarFinalAlpha = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_languageOnSpacebarFinalAlpha,
                Constants.Color.ALPHA_OPAQUE);
        final int altCodeKeyWhileTypingFadeoutAnimatorResId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_altCodeKeyWhileTypingFadeoutAnimator, 0);
        final int altCodeKeyWhileTypingFadeinAnimatorResId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_altCodeKeyWhileTypingFadeinAnimator, 0);

        mKeyPreviewDrawParams = new KeyPreviewDrawParams(mainKeyboardViewAttr);
        mKeyPreviewChoreographer = new KeyPreviewChoreographer(mKeyPreviewDrawParams);

        final int moreKeysKeyboardLayoutId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_moreKeysKeyboardLayout, 0);
        mConfigShowMoreKeysKeyboardAtTouchedPoint = mainKeyboardViewAttr.getBoolean(
                R.styleable.MainKeyboardView_showMoreKeysKeyboardAtTouchedPoint, false);

        mainKeyboardViewAttr.recycle();

        mDrawingPreviewPlacerView = drawingPreviewPlacerView;

        final LayoutInflater inflater = LayoutInflater.from(getContext());
        mMoreKeysKeyboardContainer = inflater.inflate(moreKeysKeyboardLayoutId, null);
        mAltCodeKeyWhileTypingFadeoutAnimator = loadObjectAnimator(
                altCodeKeyWhileTypingFadeoutAnimatorResId, this);
        mAltCodeKeyWhileTypingFadeinAnimator = loadObjectAnimator(
                altCodeKeyWhileTypingFadeinAnimatorResId, this);

        mKeyboardActionListener = KeyboardActionListener.EMPTY_LISTENER;

        mLanguageOnSpacebarHorizontalMargin = (int)getResources().getDimension(
                R.dimen.config_language_on_spacebar_horizontal_margin);

        // Reserve top padding for the suggestion strip. Set once here so it's stable
        // across keyboard show/hide cycles (fitsSystemWindows can override setPadding
        // if called later).
        setPadding(0, mSuggestionStripHeight, 0, mKeyboardBottomPadding);
    }

    private ObjectAnimator loadObjectAnimator(final int resId, final Object target) {
        if (resId == 0) {
            // TODO: Stop returning null.
            return null;
        }
        final ObjectAnimator animator = (ObjectAnimator)AnimatorInflater.loadAnimator(
                getContext(), resId);
        if (animator != null) {
            animator.setTarget(target);
        }
        return animator;
    }

    // --- Suggestion strip public API ---

    public void setSuggestionStripListener(final SuggestionStripView.Listener listener) {
        mSuggestionListener = listener;
    }

    public void setSuggestedWords(final SuggestedWords suggestedWords) {
        mSuggestedWords = suggestedWords;
        // Invalidate only the strip area to avoid full keyboard redraw
        invalidate(0, 0, getWidth(), mSuggestionStripHeight);
    }

    public void clearSuggestions() {
        mSuggestedWords = SuggestedWords.EMPTY;
        invalidate(0, 0, getWidth(), mSuggestionStripHeight);
    }

    public void setSuggestionStripEnabled(final boolean enabled) {
        if (mSuggestionStripEnabled == enabled) return;
        mSuggestionStripEnabled = enabled;
        final int topPadding = enabled ? mSuggestionStripHeight : 0;
        setPadding(0, topPadding, 0, mKeyboardBottomPadding);
        requestLayout();
        invalidate();
    }

    // --- Voice input status ---

    public VoiceInputState getVoiceInputState() {
        return mVoiceInputState;
    }

    public void setVoiceInputState(final VoiceInputState state) {
        setVoiceInputState(state, null);
    }

    public void setVoiceInputState(final VoiceInputState state, final String errorMessage) {
        mVoiceInputState = state;
        mVoiceErrorMessage = errorMessage;
        mDotAnimationCounter = 0;
        mDotAnimationHandler.removeCallbacks(mDotAnimationRunnable);
        mDotAnimationHandler.removeCallbacks(mErrorClearRunnable);

        if (state.getShowsAnimatedDots()) {
            mDotAnimationHandler.postDelayed(mDotAnimationRunnable, 1000);
        }

        if (state == VoiceInputState.ERROR && errorMessage != null) {
            mDotAnimationHandler.postDelayed(mErrorClearRunnable, 3000);
        }

        // Invalidate the suggestion strip for status text
        invalidate(0, 0, getWidth(), mSuggestionStripHeight);

        // Invalidate the mic key so the icon swap (mic <-> stop) takes effect
        final Keyboard keyboard = getKeyboard();
        if (keyboard != null) {
            final Key voiceKey = keyboard.getKey(Constants.CODE_VOICE_INPUT);
            if (voiceKey != null) {
                invalidateKey(voiceKey);
            }
        }
    }

    // --- Drawing ---

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);
        drawSuggestionStrip(canvas);
    }

    private void drawSuggestionStrip(final Canvas canvas) {
        if (!mSuggestionStripEnabled) return;
        final int stripHeight = mSuggestionStripHeight;
        if (stripHeight <= 0) return;

        final int width = getWidth();
        if (width <= 0) return;

        // White background for the strip area
        canvas.save();
        canvas.clipRect(0, 0, width, stripHeight);
        canvas.drawColor(Color.WHITE);

        // Voice status takes over the strip when active
        if (mVoiceInputState.getHidesSuggestions()) {
            drawVoiceStatus(canvas, width, stripHeight);
            canvas.drawLine(0, stripHeight - 1, width, stripHeight - 1, mSeparatorPaint);
            canvas.restore();
            return;
        }

        final SuggestedWords words = mSuggestedWords;
        if (words != null && !words.isEmpty() && words.size() > 0) {
            drawSuggestionWords(canvas, words, width, stripHeight);
        }

        // Bottom separator line
        canvas.drawLine(0, stripHeight - 1, width, stripHeight - 1, mSeparatorPaint);
        canvas.restore();
    }

    private void drawVoiceStatus(final Canvas canvas, final int width, final int stripHeight) {
        final float padding = mSuggestionHorizontalPadding;
        final float textY = stripHeight / 2f + mSuggestionPaint.getTextSize() / 3f;

        if (mVoiceInputState == VoiceInputState.ERROR && mVoiceErrorMessage != null) {
            final float textWidth = mSuggestionPaint.measureText(mVoiceErrorMessage);
            canvas.drawText(mVoiceErrorMessage, (width - textWidth) / 2f, textY, mSuggestionPaint);
        } else {
            final String statusText = mVoiceInputState.getStatusText();
            if (statusText != null) {
                final String dots;
                switch (mDotAnimationCounter) {
                    case 0: dots = "."; break;
                    case 1: dots = ".."; break;
                    default: dots = "..."; break;
                }
                canvas.drawText(statusText + dots, padding, textY, mSuggestionPaint);
            }
        }
    }

    private void drawSuggestionWords(final Canvas canvas, final SuggestedWords words,
            final int width, final int stripHeight) {
        final int count = words.size();

        // Layout: left = typed word, center (bold) = best suggestion, right = 2nd suggestion.
        // If only the typed word exists (no suggestions), show it in the center.
        final String centerWord;
        final String leftWord;
        final String rightWord;
        if (count > 1) {
            leftWord = words.getWord(0);     // typed word
            centerWord = words.getWord(1);   // best suggestion
            rightWord = count > 2 ? words.getWord(2) : "";
        } else {
            centerWord = count > 0 ? words.getWord(0) : "";
            leftWord = "";
            rightWord = "";
        }

        // Text baseline: vertically center in strip
        final float textHeight = -mSuggestionBoldPaint.ascent() + mSuggestionBoldPaint.descent();
        final float baseline = (stripHeight + textHeight) / 2f - mSuggestionBoldPaint.descent();

        // Three equal columns
        final float columnWidth = width / 3f;
        final float padding = mSuggestionHorizontalPadding;

        // Left suggestion
        if (!leftWord.isEmpty()) {
            mSuggestionPaint.setTextAlign(Align.LEFT);
            drawClippedText(canvas, leftWord, padding, baseline,
                    0, columnWidth, mSuggestionPaint);
        }

        // Center suggestion (bold)
        if (!centerWord.isEmpty()) {
            mSuggestionBoldPaint.setTextAlign(Align.CENTER);
            drawClippedText(canvas, centerWord, columnWidth + columnWidth / 2f, baseline,
                    columnWidth, columnWidth * 2f, mSuggestionBoldPaint);
        }

        // Right suggestion
        if (!rightWord.isEmpty()) {
            mSuggestionPaint.setTextAlign(Align.RIGHT);
            drawClippedText(canvas, rightWord, width - padding, baseline,
                    columnWidth * 2f, width, mSuggestionPaint);
        }

        // Vertical dividers between columns
        final float dividerTop = stripHeight * 0.2f;
        final float dividerBottom = stripHeight * 0.8f;
        canvas.drawLine(columnWidth, dividerTop, columnWidth, dividerBottom, mSeparatorPaint);
        canvas.drawLine(columnWidth * 2f, dividerTop, columnWidth * 2f, dividerBottom, mSeparatorPaint);
    }

    private void drawClippedText(final Canvas canvas, final String text,
            final float x, final float y,
            final float clipLeft, final float clipRight, final Paint paint) {
        canvas.save();
        canvas.clipRect(clipLeft, 0, clipRight, mSuggestionStripHeight);
        canvas.drawText(text, x, y, paint);
        canvas.restore();
    }

    // --- Touch handling ---

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        if (getKeyboard() == null) {
            return false;
        }

        // Intercept touches in the suggestion strip area
        if (mSuggestionStripEnabled && event.getY() < mSuggestionStripHeight) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                handleSuggestionStripTap(event.getX());
            }
            return true;
        }

        if (mNonDistinctMultitouchHelper != null) {
            if (event.getPointerCount() > 1 && mTimerHandler.isInKeyRepeat()) {
                // Key repeating timer will be canceled if 2 or more keys are in action.
                mTimerHandler.cancelKeyRepeatTimers();
            }
            // Non distinct multitouch screen support
            mNonDistinctMultitouchHelper.processMotionEvent(event, mKeyDetector);
            return true;
        }
        return processMotionEvent(event);
    }

    private void handleSuggestionStripTap(final float x) {
        final SuggestedWords words = mSuggestedWords;
        if (words == null || words.isEmpty() || mSuggestionListener == null) {
            return;
        }
        final int width = getWidth();
        final float columnWidth = width / 3f;
        final int count = words.size();

        // Map tap position to suggestion index.
        // Layout: left = typed (0), center = best suggestion (1), right = 2nd suggestion (2).
        // If only typed word exists, center = typed (0).
        final int index;
        if (x < columnWidth) {
            // Left column: typed word
            index = count > 1 ? 0 : -1;
        } else if (x < columnWidth * 2f) {
            // Center column: best suggestion, or typed word if no suggestions
            index = count > 1 ? 1 : 0;
        } else {
            // Right column: 2nd suggestion
            index = count > 2 ? 2 : -1;
        }

        if (index >= 0 && index < count) {
            mSuggestionListener.pickSuggestionManually(words.getInfo(index));
        }
    }

    // --- Keyboard setup ---

    private static void cancelAndStartAnimators(final ObjectAnimator animatorToCancel,
            final ObjectAnimator animatorToStart) {
        if (animatorToCancel == null || animatorToStart == null) {
            // TODO: Stop using null as a no-operation animator.
            return;
        }
        float startFraction = 0.0f;
        if (animatorToCancel.isStarted()) {
            animatorToCancel.cancel();
            startFraction = 1.0f - animatorToCancel.getAnimatedFraction();
        }
        final long startTime = (long)(animatorToStart.getDuration() * startFraction);
        animatorToStart.start();
        animatorToStart.setCurrentPlayTime(startTime);
    }

    // Implements {@link DrawingProxy#startWhileTypingAnimation(int)}.
    /**
     * Called when a while-typing-animation should be started.
     * @param fadeInOrOut {@link DrawingProxy#FADE_IN} starts while-typing-fade-in animation.
     * {@link DrawingProxy#FADE_OUT} starts while-typing-fade-out animation.
     */
    @Override
    public void startWhileTypingAnimation(final int fadeInOrOut) {
        switch (fadeInOrOut) {
        case DrawingProxy.FADE_IN:
            cancelAndStartAnimators(
                    mAltCodeKeyWhileTypingFadeoutAnimator, mAltCodeKeyWhileTypingFadeinAnimator);
            break;
        case DrawingProxy.FADE_OUT:
            cancelAndStartAnimators(
                    mAltCodeKeyWhileTypingFadeinAnimator, mAltCodeKeyWhileTypingFadeoutAnimator);
            break;
        }
    }

    public void setKeyboardActionListener(final KeyboardActionListener listener) {
        mKeyboardActionListener = listener;
        PointerTracker.setKeyboardActionListener(listener);
    }

    // TODO: We should reconsider which coordinate system should be used to represent keyboard
    // event.
    public int getKeyX(final int x) {
        return Constants.isValidCoordinate(x) ? mKeyDetector.getTouchX(x) : x;
    }

    // TODO: We should reconsider which coordinate system should be used to represent keyboard
    // event.
    public int getKeyY(final int y) {
        return Constants.isValidCoordinate(y) ? mKeyDetector.getTouchY(y) : y;
    }

    /**
     * Attaches a keyboard to this view. The keyboard can be switched at any time and the
     * view will re-layout itself to accommodate the keyboard.
     * @see Keyboard
     * @see #getKeyboard()
     * @param keyboard the keyboard to display in this view
     */
    @Override
    public void setKeyboard(final Keyboard keyboard) {
        // Remove any pending messages, except dismissing preview and key repeat.
        mTimerHandler.cancelLongPressTimers();
        super.setKeyboard(keyboard);
        mKeyDetector.setKeyboard(
                keyboard, -getPaddingLeft(), -getPaddingTop() + getVerticalCorrection());
        PointerTracker.setKeyDetector(mKeyDetector);
        mMoreKeysKeyboardCache.clear();

        mSpaceKey = keyboard.getKey(Constants.CODE_SPACE);
        final int keyHeight = keyboard.mMostCommonKeyHeight;
        mLanguageOnSpacebarTextSize = keyHeight * mLanguageOnSpacebarTextRatio;
    }

    /**
     * Enables or disables the key preview popup. This is a popup that shows a magnified
     * version of the depressed key. By default the preview is enabled.
     * @param previewEnabled whether or not to enable the key feedback preview
     * @param delay the delay after which the preview is dismissed
     */
    public void setKeyPreviewPopupEnabled(final boolean previewEnabled, final int delay) {
        mKeyPreviewDrawParams.setPopupEnabled(previewEnabled, delay);
    }

    private void locatePreviewPlacerView() {
        getLocationInWindow(mOriginCoords);
        mDrawingPreviewPlacerView.setKeyboardViewGeometry(mOriginCoords);
    }

    private void installPreviewPlacerView() {
        final View rootView = getRootView();
        if (rootView == null) {
            Log.w(TAG, "Cannot find root view");
            return;
        }
        final ViewGroup windowContentView = rootView.findViewById(android.R.id.content);
        // Note: It'd be very weird if we get null by android.R.id.content.
        if (windowContentView == null) {
            Log.w(TAG, "Cannot find android.R.id.content view to add DrawingPreviewPlacerView");
            return;
        }
        windowContentView.addView(mDrawingPreviewPlacerView);
    }

    // Implements {@link DrawingProxy#onKeyPressed(Key,boolean)}.
    @Override
    public void onKeyPressed(final Key key, final boolean withPreview) {
        key.onPressed();
        invalidateKey(key);
        if (withPreview && !key.noKeyPreview()) {
            showKeyPreview(key);
        }
    }

    private void showKeyPreview(final Key key) {
        final Keyboard keyboard = getKeyboard();
        if (keyboard == null) {
            return;
        }
        final KeyPreviewDrawParams previewParams = mKeyPreviewDrawParams;
        if (!previewParams.isPopupEnabled()) {
            previewParams.setVisibleOffset(-Math.round(keyboard.mVerticalGap));
            return;
        }

        locatePreviewPlacerView();
        getLocationInWindow(mOriginCoords);
        final int backgroundColor = mTheme.mCustomColorSupport ? mCustomColor : Color.TRANSPARENT;
        mKeyPreviewChoreographer.placeAndShowKeyPreview(key, keyboard.mIconsSet, getKeyDrawParams(),
                mOriginCoords, mDrawingPreviewPlacerView, isHardwareAccelerated(), backgroundColor);
    }

    private void dismissKeyPreviewWithoutDelay(final Key key) {
        mKeyPreviewChoreographer.dismissKeyPreview(key, false /* withAnimation */);
        invalidateKey(key);
    }

    // Implements {@link DrawingProxy#onKeyReleased(Key,boolean)}.
    @Override
    public void onKeyReleased(final Key key, final boolean withAnimation) {
        key.onReleased();
        invalidateKey(key);
        if (!key.noKeyPreview()) {
            if (withAnimation) {
                dismissKeyPreview(key);
            } else {
                dismissKeyPreviewWithoutDelay(key);
            }
        }
    }

    private void dismissKeyPreview(final Key key) {
        if (isHardwareAccelerated()) {
            mKeyPreviewChoreographer.dismissKeyPreview(key, true /* withAnimation */);
            return;
        }
        // TODO: Implement preference option to control key preview method and duration.
        mTimerHandler.postDismissKeyPreview(key, mKeyPreviewDrawParams.getLingerTimeout());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        installPreviewPlacerView();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mDotAnimationHandler.removeCallbacks(mDotAnimationRunnable);
        mDotAnimationHandler.removeCallbacks(mErrorClearRunnable);
        mDrawingPreviewPlacerView.removeAllViews();
    }

    // Implements {@link DrawingProxy@showMoreKeysKeyboard(Key,PointerTracker)}.
    //@Override
    public MoreKeysPanel showMoreKeysKeyboard(final Key key,
            final PointerTracker tracker) {
        final MoreKeySpec[] moreKeys = key.getMoreKeys();
        if (moreKeys == null) {
            return null;
        }
        Keyboard moreKeysKeyboard = mMoreKeysKeyboardCache.get(key);
        if (moreKeysKeyboard == null) {
            // {@link KeyPreviewDrawParams#mPreviewVisibleWidth} should have been set at
            // {@link KeyPreviewChoreographer#placeKeyPreview(Key,TextView,KeyboardIconsSet,KeyDrawParams,int,int[]},
            // though there may be some chances that the value is zero. <code>width == 0</code>
            // will cause zero-division error at
            // {@link MoreKeysKeyboardParams#setParameters(int,int,int,int,int,int,boolean,int)}.
            final boolean isSingleMoreKeyWithPreview = mKeyPreviewDrawParams.isPopupEnabled()
                    && !key.noKeyPreview() && moreKeys.length == 1
                    && mKeyPreviewDrawParams.getVisibleWidth() > 0;
            final MoreKeysKeyboard.Builder builder = new MoreKeysKeyboard.Builder(
                    getContext(), key, getKeyboard(), isSingleMoreKeyWithPreview,
                    mKeyPreviewDrawParams.getVisibleWidth(),
                    mKeyPreviewDrawParams.getVisibleHeight(), newLabelPaint(key));
            moreKeysKeyboard = builder.build();
            mMoreKeysKeyboardCache.put(key, moreKeysKeyboard);
        }

        final MoreKeysKeyboardView moreKeysKeyboardView =
                mMoreKeysKeyboardContainer.findViewById(R.id.more_keys_keyboard_view);
        moreKeysKeyboardView.setKeyboard(moreKeysKeyboard);
        mMoreKeysKeyboardContainer.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        final int[] lastCoords = CoordinateUtils.newInstance();
        tracker.getLastCoordinates(lastCoords);
        final boolean keyPreviewEnabled = mKeyPreviewDrawParams.isPopupEnabled()
                && !key.noKeyPreview();
        // The more keys keyboard is usually horizontally aligned with the center of the parent key.
        // If showMoreKeysKeyboardAtTouchedPoint is true and the key preview is disabled, the more
        // keys keyboard is placed at the touch point of the parent key.
        final int pointX = (mConfigShowMoreKeysKeyboardAtTouchedPoint && !keyPreviewEnabled)
                ? CoordinateUtils.x(lastCoords)
                : key.getX() + key.getWidth() / 2;
        // The more keys keyboard is usually vertically aligned with the top edge of the parent key
        // (plus vertical gap). If the key preview is enabled, the more keys keyboard is vertically
        // aligned with the bottom edge of the visible part of the key preview.
        // {@code mPreviewVisibleOffset} has been set appropriately in
        // {@link KeyboardView#showKeyPreview(PointerTracker)}.
        final int pointY = key.getY() + mKeyPreviewDrawParams.getVisibleOffset()
                + Math.round(moreKeysKeyboard.mBottomPadding);
        moreKeysKeyboardView.showMoreKeysPanel(this, this, pointX, pointY, mKeyboardActionListener);
        return moreKeysKeyboardView;
    }

    public boolean isInDraggingFinger() {
        if (isShowingMoreKeysPanel()) {
            return true;
        }
        return PointerTracker.isAnyInDraggingFinger();
    }

    public boolean isInCursorMove() {
        return PointerTracker.isAnyInCursorMove();
    }

    @Override
    public void onShowMoreKeysPanel(final MoreKeysPanel panel) {
        locatePreviewPlacerView();
        // Dismiss another {@link MoreKeysPanel} that may be being showed.
        onDismissMoreKeysPanel();
        // Dismiss all key previews that may be being showed.
        PointerTracker.setReleasedKeyGraphicsToAllKeys();
        // Dismiss sliding key input preview that may be being showed.
        panel.showInParent(mDrawingPreviewPlacerView);
        mMoreKeysPanel = panel;
    }

    public boolean isShowingMoreKeysPanel() {
        return mMoreKeysPanel != null && mMoreKeysPanel.isShowingInParent();
    }

    @Override
    public void onCancelMoreKeysPanel() {
        PointerTracker.dismissAllMoreKeysPanels();
    }

    @Override
    public void onDismissMoreKeysPanel() {
        if (isShowingMoreKeysPanel()) {
            mMoreKeysPanel.removeFromParent();
            mMoreKeysPanel = null;
        }
    }

    public void startDoubleTapShiftKeyTimer() {
        mTimerHandler.startDoubleTapShiftKeyTimer();
    }

    public void cancelDoubleTapShiftKeyTimer() {
        mTimerHandler.cancelDoubleTapShiftKeyTimer();
    }

    public boolean isInDoubleTapShiftKeyTimeout() {
        return mTimerHandler.isInDoubleTapShiftKeyTimeout();
    }

    public boolean processMotionEvent(final MotionEvent event) {
        final int index = event.getActionIndex();
        final int id = event.getPointerId(index);
        final PointerTracker tracker = PointerTracker.getPointerTracker(id);
        // When a more keys panel is showing, we should ignore other fingers' single touch events
        // other than the finger that is showing the more keys panel.
        if (isShowingMoreKeysPanel() && !tracker.isShowingMoreKeysPanel()
                && PointerTracker.getActivePointerTrackerCount() == 1) {
            return true;
        }
        tracker.processMotionEvent(event, mKeyDetector);
        return true;
    }

    public void cancelAllOngoingEvents() {
        mTimerHandler.cancelAllMessages();
        PointerTracker.setReleasedKeyGraphicsToAllKeys();
        PointerTracker.dismissAllMoreKeysPanels();
        PointerTracker.cancelAllPointerTrackers();
    }

    public void closing() {
        cancelAllOngoingEvents();
        mMoreKeysKeyboardCache.clear();
    }

    public void onHideWindow() {
        onDismissMoreKeysPanel();
    }

    public void startDisplayLanguageOnSpacebar(final boolean subtypeChanged,
            final int languageOnSpacebarFormatType) {
        if (subtypeChanged) {
            KeyPreviewView.clearTextCache();
        }
        mLanguageOnSpacebarFormatType = languageOnSpacebarFormatType;
        invalidateKey(mSpaceKey);
    }

    @Override
    protected void onDrawKeyTopVisuals(final Key key, final Canvas canvas, final Paint paint,
            final KeyDrawParams params) {
        if (key.altCodeWhileTyping()) {
            params.mAnimAlpha = mAltCodeKeyWhileTypingAnimAlpha;
        }
        // Swap mic icon to stop icon during recording
        if (key.getCode() == Constants.CODE_VOICE_INPUT
                && mVoiceInputState == VoiceInputState.RECORDING) {
            final Keyboard keyboard = getKeyboard();
            if (keyboard != null) {
                final int stopIconId = KeyboardIconsSet.getIconId(
                        KeyboardIconsSet.NAME_STOP_KEY);
                final Drawable stopIcon = keyboard.mIconsSet.getIconDrawable(stopIconId);
                if (stopIcon != null) {
                    stopIcon.setAlpha(params.mAnimAlpha);
                    final int keyWidth = key.getWidth();
                    final int keyHeight = key.getHeight();
                    final int iconWidth = Math.min(stopIcon.getIntrinsicWidth(), keyWidth);
                    final int iconHeight = stopIcon.getIntrinsicHeight();
                    final int iconX = (keyWidth - iconWidth) / 2;
                    final int iconY = (keyHeight - iconHeight) / 2;
                    drawIcon(canvas, stopIcon, iconX, iconY, iconWidth, iconHeight);
                    return;
                }
            }
        }
        super.onDrawKeyTopVisuals(key, canvas, paint, params);
        final int code = key.getCode();
        if (code == Constants.CODE_SPACE) {
            // If more than one language is enabled in current input method
            final RichInputMethodManager imm = RichInputMethodManager.getInstance();
            if (imm.hasMultipleEnabledSubtypes()) {
                drawLanguageOnSpacebar(key, canvas, paint);
            }
        }
    }

    private boolean fitsTextIntoWidth(final int width, final String text, final Paint paint) {
        final int maxTextWidth = width - mLanguageOnSpacebarHorizontalMargin * 2;
        paint.setTextScaleX(1.0f);
        final float textWidth = TypefaceUtils.getStringWidth(text, paint);
        if (textWidth < width) {
            return true;
        }

        final float scaleX = maxTextWidth / textWidth;
        if (scaleX < MINIMUM_XSCALE_OF_LANGUAGE_NAME) {
            return false;
        }

        paint.setTextScaleX(scaleX);
        return TypefaceUtils.getStringWidth(text, paint) < maxTextWidth;
    }

    // Layout language name on spacebar.
    private String layoutLanguageOnSpacebar(final Paint paint,
                                            final Subtype subtype, final int width) {
        // Choose appropriate language name to fit into the width.
        if (mLanguageOnSpacebarFormatType == LanguageOnSpacebarUtils.FORMAT_TYPE_FULL_LOCALE) {
            final String fullText =
                    LocaleResourceUtils.getLocaleDisplayNameInLocale(subtype.getLocale());
            if (fitsTextIntoWidth(width, fullText, paint)) {
                return fullText;
            }
        }

        final String middleText =
                LocaleResourceUtils.getLanguageDisplayNameInLocale(subtype.getLocale());
        if (fitsTextIntoWidth(width, middleText, paint)) {
            return middleText;
        }

        return "";
    }

    private void drawLanguageOnSpacebar(final Key key, final Canvas canvas, final Paint paint) {
        final Keyboard keyboard = getKeyboard();
        if (keyboard == null) {
            return;
        }
        final int width = key.getWidth();
        final int height = key.getHeight();
        paint.setTextAlign(Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(mLanguageOnSpacebarTextSize);
        final String language = layoutLanguageOnSpacebar(paint, keyboard.mId.mSubtype, width);
        // Draw language text with shadow
        final float descent = paint.descent();
        final float textHeight = -paint.ascent() + descent;
        final float baseline = height / 2 + textHeight / 2;
        paint.setColor(mLanguageOnSpacebarTextColor);
        paint.setAlpha(mLanguageOnSpacebarFinalAlpha);
        canvas.drawText(language, width / 2, baseline - descent, paint);
        paint.clearShadowLayer();
        paint.setTextScaleX(1.0f);
    }
}
