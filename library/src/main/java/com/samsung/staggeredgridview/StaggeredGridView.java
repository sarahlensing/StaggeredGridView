package com.samsung.staggeredgridview;

/*
 * Copyright (C) 2012 The Android Open Source Project
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
 * 
 * modified by Maurycy Wojtowicz
 * and later modified by Sarah Lensing
 * 
 */

import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.VelocityTrackerCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.EdgeEffectCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ListAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * ListView and GridView just not complex enough? Try StaggeredGridView!
 *
 * <p>StaggeredGridView presents a multi-column and multi-row grid. Each successive item from a
 * {@link com.samsung.staggeredgridview.StaggeredGridAdapter StaggeredGridAdapter} will be arranged from top to bottom
 * or left to right. The largest vertical or horizontal gap (depending on grid orientation) is always filled first .</p>
 *
 */
public class StaggeredGridView extends ViewGroup {
    private static final String TAG = "StaggeredGridView";

    private StaggeredGridAdapter mAdapter;

    public static final String STAGGERED_GRID_ORIENTATION_VERTICAL = "vertical";
    public static final String STAGGERED_GRID_ORIENTATION_HORIZONTAL = "horizontal";
    public static final String STAGGERED_GRID_DEFAULT_ORIENTATION = STAGGERED_GRID_ORIENTATION_HORIZONTAL;
    private String mOrientation = STAGGERED_GRID_DEFAULT_ORIENTATION;

    private static final int STAGGERED_GRID_DEFAULT_ITEM_MARGIN = 10;
    private int mItemMargin = STAGGERED_GRID_DEFAULT_ITEM_MARGIN;

    public static final int STAGGERED_GRID_DEFAULT_NUM_PAGES_TO_PRELOAD = 2;
    private int mNumberPagesToPreload = STAGGERED_GRID_DEFAULT_NUM_PAGES_TO_PRELOAD;

    private ArrayList<GridItem> mVisibleItems = new ArrayList<GridItem>();
    private ArrayList<GridItem> mGridItems = new ArrayList<GridItem>();

    private boolean mFastChildLayout;
    private boolean mPopulating;
    private boolean mInLayout;

    private final RecycleBin mRecycler = new RecycleBin();

    private final AdapterDataSetObserver mObserver = new AdapterDataSetObserver();

    private boolean mDataChanged;
    private int mItemCount;
    private boolean mHasStableIds;

    private ArrayList<Rect> mPosRects = new ArrayList<Rect>();
    private ItemSize mContentSize;
    private int mCurrentOffset = 0;

    private ArrayList<Integer> mSectionIndexes;

    private int mTouchSlop;
    private int mMaximumVelocity;
    private int mFlingVelocity;
    private float mLastTouchY;
    private float mLastTouchX;
    private float mTouchRemainderY;
    private float mTouchRemainderX;
    private int mActivePointerId;
    private int mMotionPosition;

    private static final int TOUCH_MODE_IDLE = 0;
    private static final int TOUCH_MODE_DRAGGING = 1;
    private static final int TOUCH_MODE_FLINGING = 2;
    private static final int TOUCH_MODE_DOWN = 3;
    private static final int TOUCH_MODE_TAP = 4;
    private static final int TOUCH_MODE_DONE_WAITING = 5;
    private static final int TOUCH_MODE_REST = 6;

    private static final int INVALID_POSITION = -1;

    private int mTouchMode;
    private final VelocityTracker mVelocityTracker = VelocityTracker.obtain();
    private final ScrollerCompat mScroller;

    private final EdgeEffectCompat mBeginningEdge;
    private final EdgeEffectCompat mEndingEdge;

    private Runnable mPendingCheckForTap;

    private ContextMenuInfo mContextMenuInfo = null;

    /**
     * The select child's view (from the adapter's getView) is enabled.
     */
    private boolean mIsChildViewEnabled;

    /**
     * The listener that receives notifications when an item is clicked.
     */
    OnItemClickListener mOnItemClickListener;

    /**
     * The listener that receives notifications when an item is long clicked.
     */
    OnItemLongClickListener mOnItemLongClickListener;

    /**
     * The last CheckForLongPress runnable we posted, if any
     */
    private CheckForLongPress mPendingCheckForLongPress;

    /**
     * Acts upon click
     */
    private PerformClick mPerformClick;

    /**
     * Rectangle used for hit testing children
     */
    private Rect mTouchFrame;

    private class GridItem extends Object {
        public long id = -1;
        public int position = -1;
        public int section = -1;
        public boolean isSection;
        public int rawPosition = -1;
        public Rect rect;
        public View view;

        @Override
        public String toString() {
            String result = "GridItem{c=" + ", id=" + id + " frame=" + rect.toString()+"}";
            return result;
        }

        @Override
        public boolean equals(Object o) {
            GridItem other = (GridItem)o;
            return ((this.id == other.id) && (this.position == other.position) && this.rect.equals(other.rect) &&
                    (this.isSection == other.isSection) && (this.section == other.section) &&
                    (this.rawPosition == other.rawPosition));
        }

    }

    public StaggeredGridView(Context context) {
        this(context, null);
    }

    public StaggeredGridView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StaggeredGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        if(attrs!=null){
            TypedArray a=getContext().obtainStyledAttributes(attrs, R.styleable.StaggeredGridView);
            if (a != null) {
                mOrientation = a.getString(R.styleable.StaggeredGridView_gridOrientation);
                if (mOrientation == null) {
                    mOrientation = STAGGERED_GRID_DEFAULT_ORIENTATION;
                }
                mNumberPagesToPreload = a.getInt(R.styleable.StaggeredGridView_numPagesToPreload, STAGGERED_GRID_DEFAULT_NUM_PAGES_TO_PRELOAD);
                mItemMargin = (int)a.getDimension(R.styleable.StaggeredGridView_itemMargin, STAGGERED_GRID_DEFAULT_ITEM_MARGIN);
            }else{
                mOrientation = STAGGERED_GRID_DEFAULT_ORIENTATION;
                mNumberPagesToPreload = STAGGERED_GRID_DEFAULT_NUM_PAGES_TO_PRELOAD;
                mItemMargin = STAGGERED_GRID_DEFAULT_ITEM_MARGIN;
            }
        }

        final ViewConfiguration vc = ViewConfiguration.get(context);
        mTouchSlop = vc.getScaledTouchSlop();
        mMaximumVelocity = vc.getScaledMaximumFlingVelocity();
        mFlingVelocity = vc.getScaledMinimumFlingVelocity();
        mScroller = ScrollerCompat.from(context);

        mBeginningEdge = new EdgeEffectCompat(context);
        mEndingEdge = new EdgeEffectCompat(context);

        setWillNotDraw(false);
        setClipToPadding(false);
        this.setFocusableInTouchMode(false);

    }

    public int getItemMargin() {
        return mItemMargin;
    }

    /**
     * Set the margin between items in pixels. This margin is applied
     * both vertically and horizontally.
     *
     * @param marginPixels Spacing between items in pixels
     */
    public void setItemMargin(int marginPixels) {
        final boolean needsReload = marginPixels != mItemMargin;
        mItemMargin = marginPixels;
        if (needsReload) {
            reloadGrid();
        }
    }

    public String getGridOrientation() {
        return mOrientation;
    }

    public void setGridOrientation(String orientation) {
        final boolean needsReload = orientation != mOrientation;
        mOrientation = orientation;
        if (needsReload) {
            reloadGrid();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        mVelocityTracker.addMovement(ev);
        final int action = ev.getAction() & MotionEventCompat.ACTION_MASK;
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mVelocityTracker.clear();
                abortScrollerAnimation();
                mLastTouchY = ev.getY();
                mLastTouchX = ev.getX();
                mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
                mTouchRemainderY = 0;
                mTouchRemainderX = 0;
                if (mTouchMode == TOUCH_MODE_FLINGING) {
                    // Catch!
                    mTouchMode = TOUCH_MODE_DRAGGING;
                    return true;
                }
                break;

            case MotionEvent.ACTION_MOVE: {
                final int index = MotionEventCompat.findPointerIndex(ev, mActivePointerId);
                if (index < 0) {
                    Log.e(TAG, "onInterceptTouchEvent could not find pointer with id " +
                            mActivePointerId + " - did StaggeredGridView receive an inconsistent " +
                            "event stream?");
                    return false;
                }
                if (mOrientation.equals(STAGGERED_GRID_ORIENTATION_VERTICAL)) {
                    final float y = MotionEventCompat.getY(ev, index);
                    final float dy = y - mLastTouchY + mTouchRemainderY;
                    final int deltaY = (int) dy;
                    mTouchRemainderY = dy - deltaY;

                    if (Math.abs(dy) > mTouchSlop) {
                        mTouchMode = TOUCH_MODE_DRAGGING;
                        return true;
                    }
                }
                else {
                    final float x = MotionEventCompat.getX(ev, index);
                    final float dx = x - mLastTouchX + mTouchRemainderX;
                    final int deltaX = (int) dx;
                    mTouchRemainderX = dx - deltaX;
                    if (Math.abs(dx) > mTouchSlop) {
                        mTouchMode = TOUCH_MODE_DRAGGING;
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public boolean vertical() {
        return mOrientation.equals(STAGGERED_GRID_ORIENTATION_VERTICAL);
    }

    private void releaseEdges() {
        if (mBeginningEdge != null) {
            mBeginningEdge.onRelease();
        }

        if (mEndingEdge != null) {
            mEndingEdge.onRelease();
        }
    }

    private int calculateDeltaScroll(MotionEvent ev, int pointerIndex) {
        int ret;
        if (vertical()) {
            final float y = MotionEventCompat.getY(ev, pointerIndex);
            final float dy = y - mLastTouchY + mTouchRemainderY;
            final int deltaY = (int) dy;
            mTouchRemainderY = dy - deltaY;

            if (Math.abs(dy) > mTouchSlop) {
                mTouchMode = TOUCH_MODE_DRAGGING;
            }
            ret = deltaY;

            if (mTouchMode == TOUCH_MODE_DRAGGING) {
                mLastTouchY = y;
            }
        }
        else {
            final float x = MotionEventCompat.getX(ev, pointerIndex);
            final float dx = x - mLastTouchX + mTouchRemainderX;
            final int deltaX = (int) dx;
            mTouchRemainderX = dx - deltaX;


            if (Math.abs(dx) > mTouchSlop) {
                mTouchMode = TOUCH_MODE_DRAGGING;
            }
            ret = deltaX;

            if (mTouchMode == TOUCH_MODE_DRAGGING) {
                mLastTouchX = x;
            }
        }
        return ret;
    }

    private void doScroll(int delta) {
        final boolean contentFits = contentFits();
        if (contentFits) {
            return;
        }
        offsetChildren(delta);
        layoutGridItems();
        recycleOffscreenItems();
    }

    private void doScrollFling() {
        mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
        if (vertical()) {
            final float velocityY = VelocityTrackerCompat.getYVelocity(mVelocityTracker, mActivePointerId);
            if (Math.abs(velocityY) > mFlingVelocity) { // TODO
                mTouchMode = TOUCH_MODE_FLINGING;
                mScroller.fling(0, 0, 0, (int) velocityY, 0, 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
                mLastTouchY = 0;
                mLastTouchX = 0;
                invalidate();
            } else {
                mTouchMode = TOUCH_MODE_IDLE;
            }
        }
        else {
            final float velocityX = VelocityTrackerCompat.getXVelocity(mVelocityTracker, mActivePointerId);
            if (Math.abs(velocityX) > mFlingVelocity) { // TODO
                mTouchMode = TOUCH_MODE_FLINGING;
                mScroller.fling(0, 0, (int) velocityX, 0, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 0);
                mLastTouchY = 0;
                mLastTouchX = 0;
                invalidate();
            } else {
                mTouchMode = TOUCH_MODE_IDLE;
            }
        }
    }

    private void abortScrollerAnimation() {
        mScroller.abortAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        mVelocityTracker.addMovement(ev);
        final int action = ev.getAction() & MotionEventCompat.ACTION_MASK;

        switch (action) {
            case MotionEvent.ACTION_DOWN:

                mVelocityTracker.clear();
                abortScrollerAnimation();
                mLastTouchY = ev.getY();
                mLastTouchX = ev.getX();
                int motionPosition = pointToPosition((int) mLastTouchX, (int) mLastTouchY);
                mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
                mTouchRemainderY = 0;
                mTouchRemainderX = 0;

                if(mTouchMode != TOUCH_MODE_FLINGING && !mDataChanged && motionPosition >= 0 && getAdapter().isEnabled(motionPosition)){
                    mTouchMode = TOUCH_MODE_DOWN;

                    if (mPendingCheckForTap == null) {
                        mPendingCheckForTap = new CheckForTap();
                    }

                    postDelayed(mPendingCheckForTap, ViewConfiguration.getTapTimeout());
                }

                mMotionPosition = motionPosition;
                invalidate();
                break;

            case MotionEvent.ACTION_MOVE: {
                final int index = MotionEventCompat.findPointerIndex(ev, mActivePointerId);
                if (index < 0) {
                    Log.e(TAG, "onInterceptTouchEvent could not find pointer with id " +
                            mActivePointerId + " - did StaggeredGridView receive an inconsistent " +
                            "event stream?");
                    return false;
                }
                int delta = calculateDeltaScroll(ev, index);
                doScroll(delta);
            } break;

            case MotionEvent.ACTION_CANCEL:
                mTouchMode = TOUCH_MODE_IDLE;
                setPressed(false);

                final Handler handler = getHandler();
                if (handler != null) {
                    handler.removeCallbacks(mPendingCheckForLongPress);
                }

                releaseEdges();

                mTouchMode = TOUCH_MODE_IDLE;
                break;

            case MotionEvent.ACTION_UP: {
                doScrollFling();
            } break;
        }
        return true;
    }

    private boolean isLower(GridItem gridItem, int lowest) {
        if (vertical()) {
            return gridItem.rect.bottom > lowest;
        }
        else {
            return gridItem.rect.right > lowest;
        }
    }

    private int getOverhang() {
        int lowest = 0;
        for (int i = 0; i < mVisibleItems.size(); i++) {
            GridItem gridItem = mVisibleItems.get(i);
            if (gridItem == null) {
                Log.d("GRID ITEM NULL, WHY", "");
                continue;
            }
            if (isLower(gridItem, lowest)) {
                lowest = vertical()?gridItem.rect.bottom:gridItem.rect.right;
            }
        }

        if (vertical()) {
            return lowest - getEndingBottom();
        }
        else {
            return lowest - getEndingRight();
        }
    }

    /**
     *
     * @param delta Pixels that content should move by
     * @return true if the movement completed, false if it was stopped prematurely.
     */
    private boolean trackMotionScroll(int delta, boolean allowOverScroll) {
        final boolean contentFits = contentFits();
        if (contentFits) {
            return true;
        }
        final int allowOverhang = Math.abs(delta);

        final int overScrolledBy;
        final int movedBy;
        if (!contentFits) {
            final int overhang;
            mPopulating = true;

            overhang = getOverhang();
            boolean towardsBeginning = (delta > 0);
            movedBy = Math.min(overhang, allowOverhang);
            doScroll(towardsBeginning ? movedBy : -movedBy);

            mPopulating = false;
            overScrolledBy = allowOverhang - overhang;
        } else {
            overScrolledBy = allowOverhang;
            movedBy = 0;
        }

        if (allowOverScroll) {
            final int overScrollMode = ViewCompat.getOverScrollMode(this);

            if (overScrollMode == ViewCompat.OVER_SCROLL_ALWAYS ||
                    (overScrollMode == ViewCompat.OVER_SCROLL_IF_CONTENT_SCROLLS && !contentFits)) {
                if (overScrolledBy > 0) {
                    pullEdges(delta);
                    invalidate();
                }
            }
        }

        return delta == 0 || movedBy != 0;
    }

    private void pullEdges(int delta) {
        EdgeEffectCompat edge = delta > 0 ? mBeginningEdge : mEndingEdge;
        float amountToPull;
        if (vertical()) {
            amountToPull = (float) Math.abs(delta) / getHeight();
        }
        else {
            amountToPull = (float) Math.abs(delta) / getWidth();
        }
        edge.onPull(amountToPull);
    }

    private final boolean contentFits() {
        if (mContentSize == null) {
            return true;
        }
        if (vertical()) {
            return mContentSize.height <= getHeight();
        }
        else {
            return mContentSize.width <= getWidth();
        }
    }

    private void recycleAllViews() {
        for (int i = 0; i < getChildCount(); i++) {
            mRecycler.addScrap(getChildAt(i));
        }

//        if (mInLayout) {
        removeAllViewsInLayout();
//        } else {
//            removeAllViews();
//        }
    }

    private Rect getCurrViewportRect() {
        if (vertical()) {
            return new Rect(0, Math.max(0, mCurrentOffset-defaultAmountToLayout()), getWidth(), Math.min(mContentSize.height, mCurrentOffset+defaultAmountToLayout()));
        }
        else {
            return new Rect(Math.max(mCurrentOffset-defaultAmountToLayout(), 0), 0, Math.min(mContentSize.width, mCurrentOffset+defaultAmountToLayout()), getHeight());
        }
    }

    private ArrayList<GridItem> getNextOffscreenItems() {
        Rect currViewport = getCurrViewportRect();
        ArrayList<GridItem> ret = new ArrayList<GridItem>();
        for (GridItem item : mVisibleItems) {
            if (!currViewport.contains(item.rect)) {
                ret.add(item);
            }
        }
        return ret;
    }

    private void recycleOffscreenItems() {
        ArrayList<GridItem> nowOffscreens = getNextOffscreenItems();
        for (GridItem item : nowOffscreens) {
            View view = item.view;
            removeViewInLayout(view);
//            Log.d("MYVIEWCOUNT", String.valueOf(this.getChildCount()));
            mRecycler.addScrap(view);
            item.view = null;
            mVisibleItems.remove(item);
        }
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {

            if (vertical()) {
                final int y = mScroller.getCurrY();
                final int dy = (int) (y - mLastTouchY);
                mLastTouchY = y;
                final boolean stopped = !trackMotionScroll(dy, false);

                if (!stopped && !mScroller.isFinished()) {
                    postInvalidate();
                } else {
                    if (stopped) {
                        final int overScrollMode = ViewCompat.getOverScrollMode(this);
                        if (overScrollMode != ViewCompat.OVER_SCROLL_NEVER) {
                            absorbEdges(dy);
                            postInvalidate();
                        }
                        abortScrollerAnimation();
                    }
                    mTouchMode = TOUCH_MODE_IDLE;
                }
            }
            else {
                final int x = mScroller.getCurrX();
                final int dx = (int) (x - mLastTouchX);
                mLastTouchX = x;
                final boolean stopped = !trackMotionScroll(dx, false);

                if (!stopped && !mScroller.isFinished()) {
                    postInvalidate();
                } else {
                    if (stopped) {
                        final int overScrollMode = ViewCompat.getOverScrollMode(this);
                        if (overScrollMode != ViewCompat.OVER_SCROLL_NEVER) {
                            absorbEdges(dx);
                            postInvalidate();
                        }
                        abortScrollerAnimation();
                    }
                    mTouchMode = TOUCH_MODE_IDLE;
                }
            }
        }
    }

    private void absorbEdges(int delta) {
        EdgeEffectCompat edge = delta > 0 ? mBeginningEdge : mEndingEdge;
        int amount = Math.abs((int) mScroller.getCurrVelocity());
        edge.onAbsorb(amount);
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        if (mBeginningEdge != null) {
            boolean needsInvalidate = false;
            if (!mBeginningEdge.isFinished()) {
                mBeginningEdge.draw(canvas);
                needsInvalidate = true;
            }
            if (!mEndingEdge.isFinished()) {
                final int restoreCount = canvas.save();
                if (vertical()) {
                    final int width = getWidth();
                    canvas.translate(-width, getHeight());
                    canvas.rotate(180, width, 0);
                }
                else {
                    final int height = getHeight();
                    canvas.translate(getWidth(), -height);
                    canvas.rotate(180, 0, height);
                }
                mEndingEdge.draw(canvas);
                canvas.restoreToCount(restoreCount);
                needsInvalidate = true;
            }

            if (needsInvalidate) {
                invalidate();
            }
        }
    }

    public void beginFastChildLayout() {
        mFastChildLayout = true;
    }

    public void endFastChildLayout() {
        mFastChildLayout = false;
        layoutGridItems();
    }

    @Override
    public void requestLayout() {
        if (!mPopulating && !mFastChildLayout) {
            super.requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if (widthMode != MeasureSpec.EXACTLY) {
            widthMode = MeasureSpec.EXACTLY;
        }
        if (heightMode != MeasureSpec.EXACTLY) {
            heightMode = MeasureSpec.EXACTLY;
        }

        setMeasuredDimension(widthSize, heightSize);
    }

    private void updateEdgeSizes(int l, int t, int r, int b) {
        final int width = r - l;
        final int height = b - t;
        mBeginningEdge.setSize(width, height);
        mEndingEdge.setSize(width, height);
    }

    private boolean shouldLayout() {
        return (getWidth() != 0 && getHeight() != 0);
    }

    private void prepareToBuildItems() {
        mPosRects.clear();
        mGridItems.clear();
        mVisibleItems.clear();
        mContentSize = new ItemSize(0,0);
        mCurrentOffset = 0;
        recycleAllViews();
    }

    private int defaultAmountToLayout() {
        if (vertical()) {
            return getHeight() * mNumberPagesToPreload;
        }
        return getWidth() * mNumberPagesToPreload;
    }

    private void reloadGrid() {
        prepareToBuildItems();
        buildGridItems();
        layoutGridItems();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        mInLayout = true;
        if (shouldLayout()) {
            reloadGrid();
        }
        mInLayout = false;
        updateEdgeSizes(l, t, r, b);
    }

    private boolean ensureAvailableSpace(int nextLeft, int nextTop, int itemSpace) {
        boolean ret = true;

        int clearedSpace = 0;
        int index = 0;

        if (mPosRects.size() == 0) { //we have space because its the first item we are laying out
            return ret;
        }
        boolean hadClearedSpaceBefore = false; //because we need consecutive space
        while (clearedSpace < itemSpace && index < mPosRects.size()) {
            Rect rect = mPosRects.get(index);
            if (vertical()) {
                int rectLeft = rect.left;
                if (rectLeft >= nextLeft) {
                    int rectBottom = rect.bottom;
                    if (rectBottom < nextTop) {
                        int rectWidth = rect.right - rectLeft;
                        int rectTotalWidth = rectWidth + mItemMargin;
                        clearedSpace+= rectTotalWidth;
                        hadClearedSpaceBefore = true;
                    }
                    else if (hadClearedSpaceBefore){
                        clearedSpace = 0;
                    }
                }
            }
            else {
                int rectTop = rect.top;
                if (rectTop >= nextTop) {
                    int rectRight = rect.right;
                    if (rectRight < nextLeft) {
                        int rectHeight = rect.bottom - rectTop;
                        int rectTotalHeight = rectHeight + mItemMargin;
                        clearedSpace+= rectTotalHeight;
                        hadClearedSpaceBefore = true;
                    }
                    else if (hadClearedSpaceBefore){
                        clearedSpace = 0;
                    }
                }
            }
            index++;
        }
        ret = clearedSpace >= itemSpace;
        return ret;
    }

    public class CustomComparator implements Comparator<Rect> {
        @Override
        public int compare(Rect r1, Rect r2) {
            if (vertical()) {
                return r1.bottom - r2.bottom;
            }
            return r1.right - r2.right;
        }
    }

    private ArrayList<Rect> sortedRects() {
        ArrayList<Rect> sortedPosRects = new ArrayList<Rect>();
        sortedPosRects.addAll(mPosRects);
        Collections.copy(sortedPosRects,mPosRects);
        Collections.sort(sortedPosRects, new CustomComparator());
        return sortedPosRects;
    }

    private Point getNextPoint(int itemSpace, boolean isSection) {
        Point ret;
        if (mPosRects.size() == 0) {
            ret = new Point(getBeginningLeft(), getBeginningTop());
        }
        else {
            if (vertical()) {
                ArrayList<Rect> sorted = sortedRects();
                if (isSection) {
                    Rect lastPosRect = sorted.get(mPosRects.size()-1);
                    return new Point(getBeginningLeft(), lastPosRect.bottom + mItemMargin);
                }
                Rect lastPosRect = mPosRects.get(mPosRects.size()-1);
                //not a lot of pos rects yet so just get next space to right
                if (getEndingRight() - lastPosRect.right >= itemSpace) {
                    return new Point(lastPosRect.right + mItemMargin, getBeginningTop());
                }
                else {
                    for (Rect rect : sortedRects()) {
                        if (ensureAvailableSpace(rect.left, rect.bottom + mItemMargin, itemSpace)) {
                            return new Point(rect.left, rect.bottom + mItemMargin);
                        }
                    }
                    Rect lastRect = sorted.get(sorted.size()-1);
                    ret = new Point(getBeginningLeft(), lastRect.bottom + mItemMargin);
                }
            }
            else {
                ArrayList<Rect> sorted = sortedRects();
                if (isSection) {
                    Rect lastPosRect = sorted.get(mPosRects.size()-1);
                    return new Point(lastPosRect.right + mItemMargin, getBeginningTop());
                }
                Rect lastPosRect = mPosRects.get(mPosRects.size()-1);
                //not a lot of pos rects yet so just get next space below
                if (getEndingBottom() - lastPosRect.bottom >= itemSpace) {
                    return new Point(getBeginningLeft(), lastPosRect.bottom + mItemMargin);
                }
                else {
                    for (Rect rect : sortedRects()) {
                        if (ensureAvailableSpace(rect.right + mItemMargin, rect.top, itemSpace)) {
                            return new Point(rect.right + mItemMargin, rect.top);
                        }
                    }
                    Rect lastRect = sorted.get(sorted.size()-1);
                    ret = new Point(lastRect.right + mItemMargin, getBeginningTop());
                }
            }
        }
        return ret;
    }

    private int getBeginningTop() {
        return getPaddingTop() + mItemMargin;
    }

    private int getBeginningLeft() {
        return getPaddingLeft() + mItemMargin;
    }

    private int getEndingRight() {
        return getWidth() - getPaddingRight() - mItemMargin;
    }

    private int getEndingBottom() {
        return getHeight() - getPaddingBottom() - mItemMargin;
    }

    private boolean isIrrelevant(int lastRectStart, int lastRectEnd, int rectStart, int rectEnd) {
        return lastRectStart >= rectStart && lastRectEnd <= rectEnd;
    }

    private boolean noMoreIrrelevants(int lastRectStart, int rectEnd) {
        return lastRectStart > rectEnd;
    }

    private ArrayList<Rect> calculateIrrelevantRects(Rect rect) {
        ArrayList<Rect> ret = new ArrayList<Rect>();
        int index = 0;
        int lastRectStart;
        int lastRectEnd;
        int rectStart;
        int rectEnd;

        if (vertical()) {
            rectStart = rect.left;
            rectEnd = rect.right;
        }
        else {
            rectStart = rect.top;
            rectEnd = rect.bottom;
        }

        while (index < mPosRects.size()) {
            Rect potentialIrrelevant = mPosRects.get(index);

            if (vertical()) {
                lastRectStart = potentialIrrelevant.left;
                lastRectEnd = potentialIrrelevant.right;
            }
            else {
                lastRectStart = potentialIrrelevant.top;
                lastRectEnd = potentialIrrelevant.bottom;
            }

            if (isIrrelevant(lastRectStart, lastRectEnd, rectStart, rectEnd)) {
                ret.add(potentialIrrelevant);
            }
            else if (noMoreIrrelevants(lastRectStart, rectEnd)) { //there are no more irrelevants bc we have accounted for rect's height
                break;
            }
            index++;
        }
        return ret;
    }

    private boolean shouldAddRectBefore(Rect rect, Rect r) {
        if (vertical()) {
            return rect.right <= r.right;
        }
        else {
            return rect.bottom <= r.bottom;
        }
    }

    private int nextAddIndexForRect(Rect rect) {
        int index = mPosRects.size();
        for (int i = 0; i < mPosRects.size(); i++) {
            Rect r = mPosRects.get(i);
            if (shouldAddRectBefore(rect, r)) {
                index = i;
                break;
            }
        }
        return index;
    }

    private boolean rectOverlapsRect(Rect r1, Rect r2) {
        if (vertical()) {
            return r2.left < r1.right;
        }
        else {
            return r2.top < r1.bottom;
        }
    }

    private Rect getTrimmedRect(Rect r1, Rect r2) {
        if (vertical()) {
            return new Rect(r1.right + mItemMargin, r2.top, r2.right, r2.bottom);
        }
        else {
            return new Rect(r2.left,r1.bottom + mItemMargin, r2.right, r2.bottom);
        }
    }

    private void trimAnyPosRectOverlap() {
        if (mPosRects.size() < 2) {
            return; //no work to be done
        }
        for (int i = 0; i < mPosRects.size()-1; i++) {
            Rect r1 = mPosRects.get(i);
            Rect r2 = mPosRects.get(i+1);
            if (rectOverlapsRect(r1, r2)) { //need to trim
                //trim
                Rect trimmed = getTrimmedRect(r1, r2);
                mPosRects.remove(i+1);
                mPosRects.add(i+1, trimmed);
            }
        }
    }

    private void updatePosRects(Rect rect) {
        int addIndex;

        //1) remove irrelevant rects

        //remove rects that rect will render irrelevant in next views position calculations
        ArrayList<Rect> irrelevantRects = calculateIrrelevantRects(rect);
        if (irrelevantRects.size() > 0) {
            addIndex = mPosRects.indexOf(irrelevantRects.get(0));
            mPosRects.removeAll(irrelevantRects);
        }
        else {
            addIndex = nextAddIndexForRect(rect);
        }

        // 2) add this rect

        //add rect so can be used in future position calculations
        mPosRects.add(addIndex, rect);

        // 3) modify rects so that there is no overlap (ie. in case that one element takes whole height and then another takes half, modify whole element's top for future calculations
        trimAnyPosRectOverlap();
    }

    private ItemSize getDefaultContentSize(Rect rect) {
        ItemSize ret;
        if (vertical()) {
            ret = new ItemSize(getWidth(), rect.bottom);
        }
        else {
            ret = new ItemSize(rect.right, getHeight());
        }
        return ret;
    }

    private void updateContentSize(Rect rect) {
        if (mContentSize == null) {
            mContentSize = getDefaultContentSize(rect);
        }
        else {
            int currMeasure;
            int proposedMeasure;
            if (vertical()) {
                currMeasure = mContentSize.height;
                proposedMeasure = rect.bottom;
            }
            else {
                currMeasure = mContentSize.width;
                proposedMeasure = rect.right;
            }
            if (proposedMeasure > currMeasure) {
                if (vertical()) {
                    mContentSize.height = proposedMeasure;
                }
                else {
                    mContentSize.width = proposedMeasure;
                }
            }
        }
    }

    private Rect calculateNextItemRect(ItemSize size, boolean isSection) {
        int itemWidth = size.width;
        int itemHeight = size.height;

        Point point = getNextPoint(itemHeight, isSection);
        int nextLeft = point.x;
        int nextTop = point.y;

        int itemLeft = nextLeft;
        int itemRight = itemLeft + itemWidth;
        final int itemTop = nextTop;
        final int itemBottom = itemTop + itemHeight;

        Rect ret = new Rect(itemLeft, itemTop, itemRight, itemBottom);
        updatePosRects(ret);
        updateContentSize(ret);
        return ret;
    }

    private StaggeredGridSectionAdapter getSectionAdapter() {
        return (StaggeredGridSectionAdapter)mAdapter;
    }

    private boolean hasSectionAdapter() {
        return mAdapter instanceof StaggeredGridSectionAdapter;
    }

    private ArrayList<Integer> getSectionsFromAdapter() {
        ArrayList<Integer> ret = new ArrayList<Integer>();
        if (hasSectionAdapter()) {
            for (int i = 0; i < getSectionAdapter().getSectionCount(); i++) {
                ret.add(getSectionAdapter().getItemCountForSection(i));
            }
            return ret;
        }
        ret.add(mAdapter.getCount());
        return ret;
    }

    private void buildGridItems() {
        if (mAdapter == null) {
            return;
        }

        mSectionIndexes = getSectionsFromAdapter();

        int sectionStart = 0;
        int sectionEnd = mSectionIndexes.size();

        int rawPosition = 0;

        for (int i = sectionStart; i < sectionEnd; i++) {
            if (hasSectionAdapter()) {
                GridItem sectionItem = new GridItem();
                sectionItem.id = getSectionAdapter().getSectionID(i);
                sectionItem.position = i;
                sectionItem.section = i;
                sectionItem.isSection = true;
                sectionItem.rawPosition = rawPosition;
                ItemSize sectionSize = getSectionAdapter().getSectionSize(i);
                sectionItem.rect = calculateNextItemRect(sectionSize, sectionItem.isSection);
                mGridItems.add(i,sectionItem);
            }

            int numItemsInSection = mSectionIndexes.get(i);
            for (int j = 0; j < numItemsInSection; j++) {
                rawPosition++;

                GridItem item = new GridItem();
                item.id = mAdapter.getItemId(j);
                item.position = j;
                item.section = i;
                item.isSection = false;
                item.rawPosition = rawPosition;
                ItemSize size = mAdapter.getItemSize(j);
                item.rect = calculateNextItemRect(size, item.isSection);
                mGridItems.add(j,item);
            }
            rawPosition++;
        }
    }

    private boolean shouldLayout(Rect itemRect, Rect layoutRect) {
        return Rect.intersects(layoutRect, itemRect);
    }

    private Rect getLayoutRect(int start, int end) {
        if (vertical()) {
            return new Rect(getBeginningLeft(), start, getEndingRight(), end);
        }
        else {
            return new Rect(start, getBeginningTop(), end, getEndingBottom());
        }
    }

    private View getViewForGridItem(GridItem item) {
        int position = item.position;
        final View child;
        if (item.isSection) {
            child = obtainSectionView(position, null, item.rawPosition);
        }
        else {
            child = obtainView(position, null, item.rawPosition);
        }

        if(child == null) {
            Log.d("PROBLEM", "NULL CHILD");
        }

        LayoutParams lp = (LayoutParams) child.getLayoutParams();
        if (lp == null)  {
            lp = this.generateDefaultLayoutParams();
            child.setLayoutParams(lp);
        }
        child.setTag(R.string.GRID_ITEM_TAG, item);
        if (child.getParent() != this) {
//            if (mInLayout) {
            addViewInLayout(child, -1, lp); //always addViewInLayout so we dont trigger onLayout
//            } else {
//                addView(child);
//            }
//            Log.d("MYVIEWCOUNT", String.valueOf(this.getChildCount()));
        }
        return child;
    }

    private void layoutGridItem(GridItem item) {
        View child = getViewForGridItem(item);
        child.measure(item.rect.width(),item.rect.height());
        if (vertical()) {
            child.layout(item.rect.left, item.rect.top-mCurrentOffset, item.rect.right, item.rect.bottom-mCurrentOffset);
        }
        else {
            child.layout(item.rect.left-mCurrentOffset, item.rect.top, item.rect.right-mCurrentOffset, item.rect.bottom);
        }
        item.view = child;
//        mVisibleItems.add(item.position, item);
        mVisibleItems.add(item);
    }

    private ArrayList<GridItem> getNextVisibleItems(int start, int end) {
        ArrayList<GridItem> ret = new ArrayList<GridItem>();
        Rect layoutRect = getLayoutRect(start, end);
        for (int i = 0; i < mGridItems.size(); i++) {
            GridItem item = mGridItems.get(i);
            if (shouldLayout(item.rect, layoutRect)) {
                ret.add(item);
            }
        }
        return ret;
    }

    private boolean needsLayout(ArrayList<GridItem>nextVisibles) {
        return !nextVisibles.equals(mVisibleItems);
    }

    private void layoutItems(ArrayList<GridItem>nextVisibles) {
        if (nextVisibles.size() == 0) {
            return; //no items to add to grid
        }
        for (int i = 0; i < nextVisibles.size(); i++) {
            GridItem item = nextVisibles.get(i);
            if (!mVisibleItems.contains(item)) {
                layoutGridItem(item);
            }
        }
    }

    private void layoutGridItems() {
        layoutGridItems(mCurrentOffset, mCurrentOffset+defaultAmountToLayout());
    }

    private void layoutGridItems(int start, int end) {
        ArrayList<GridItem> nextVisibles = getNextVisibleItems(start, end);
        if (needsLayout(nextVisibles)) {
            layoutItems(nextVisibles);
        }
    }

    private int getMinAllowedOffset() {
        return 0;
    }

    private int getMaxAllowedOffset() {
        if (vertical()) {
            return mContentSize.height - getHeight() + mItemMargin;
        }
        else {
            return mContentSize.width - getWidth() + mItemMargin;
        }
    }

    final void offsetChildren(int offset) {
//        Log.d("CURR OFFSET", String.valueOf(mCurrentOffset));

        int nextPredictedOffset = mCurrentOffset - offset;
        if (nextPredictedOffset < getMinAllowedOffset()) {
            offset = mCurrentOffset;
        }
        else if (nextPredictedOffset > getMaxAllowedOffset()) {
            offset = mCurrentOffset - getMaxAllowedOffset();
        }

        if (offset != 0) {
            final int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                final View child = getChildAt(i);
                offsetChild(child, offset);
            }
        }
        mCurrentOffset-=offset;
    }

    final void offsetChild(View child, int offset) {
        if (vertical()) {
            int nextTop = child.getTop() + offset;
            int nextBottom = child.getBottom() + offset;
            child.layout(child.getLeft(), nextTop, child.getRight(), nextBottom);
        }
        else {
            int nextLeft = child.getLeft() + offset;
            int nextRight = child.getRight() + offset;
            child.layout(nextLeft, child.getTop(), nextRight, child.getBottom());
        }
    }


    final View obtainSectionView(int position, View optScrap, int rawPosition) {
        View view = mRecycler.getTransientStateView(position);
        if (view != null) {
            return view;
        }

        if(position >= getSectionAdapter().getCount()){
            Log.d("PROBLEM", "ASKING FOR POSITION THAT DOESNT EXIST");
            return null;
        }

        // Reuse optScrap if it's of the right type (and not null)
        final int optType = optScrap != null ? ((LayoutParams) optScrap.getLayoutParams()).viewType : -1;
        final int positionViewType = getAdapterViewTypeCount() - 1; //position
        final View scrap = optType == positionViewType ? optScrap : mRecycler.getScrapView(positionViewType);

        view = getSectionAdapter().getSectionView(position, scrap, this);

        if (view != scrap && scrap != null) {
            // The adapter didn't use it; put it back.
            mRecycler.addScrap(scrap);
        }

        ViewGroup.LayoutParams lp = view.getLayoutParams();

        if (view.getParent() != this) {
            if (lp == null) {
                lp = generateDefaultLayoutParams();
            } else if (!checkLayoutParams(lp)) {
                lp = generateLayoutParams(lp);
            }
        }

        final LayoutParams sglp = (LayoutParams) lp;
        sglp.position = rawPosition;
        sglp.viewType = positionViewType;
        view.setLayoutParams(sglp);

        return view;
    }

    /**
     * Obtain a populated view from the adapter. If optScrap is non-null and is not
     * reused it will be placed in the recycle bin.
     *
     * @param position position to get view for
     * @param optScrap Optional scrap view; will be reused if possible
     * @return A new view, a recycled view from mRecycler, or optScrap
     */
    final View obtainView(int position, View optScrap, int rawPosition) {
        View view = mRecycler.getTransientStateView(position);
        if (view != null) {
            return view;
        }

        if(position >= mAdapter.getCount()){
            Log.d("PROBLEM", "ASKING FOR POSITION THAT DOESNT EXIST");
            return null;
        }

        // Reuse optScrap if it's of the right type (and not null)
        final int optType = optScrap != null ? ((LayoutParams) optScrap.getLayoutParams()).viewType : -1;
        final int positionViewType = mAdapter.getItemViewType(position);
        final View scrap = optType == positionViewType ? optScrap : mRecycler.getScrapView(positionViewType);

        view = mAdapter.getView(position, scrap, this);

        if (view != scrap && scrap != null) {
            // The adapter didn't use it; put it back.
            mRecycler.addScrap(scrap);
        }

        ViewGroup.LayoutParams lp = view.getLayoutParams();

        if (view.getParent() != this) {
            if (lp == null) {
                lp = generateDefaultLayoutParams();
            } else if (!checkLayoutParams(lp)) {
                lp = generateLayoutParams(lp);
            }
        }

        final LayoutParams sglp = (LayoutParams) lp;
        sglp.position = rawPosition;
        sglp.viewType = positionViewType;

        return view;
    }

    public ListAdapter getAdapter() {
        return mAdapter;
    }

    private int getSectionAdapterViewTypeCount() {
        return 1; //for now only support one view type for header
    }

    private int getAdapterViewTypeCount() {
        if (hasSectionAdapter()) {
            return mAdapter.getViewTypeCount() + getSectionAdapterViewTypeCount();
        }
        return mAdapter.getViewTypeCount();
    }

    public void setAdapter(StaggeredGridAdapter adapter) {
        if (mAdapter != null) {
            mAdapter.unregisterDataSetObserver(mObserver);
        }
        // TODO: If the new adapter says that there are stable IDs, remove certain layout records
        // and onscreen views if they have changed instead of removing all of the state here.
        clearAllState();
        mAdapter = adapter;
        mDataChanged = true;

        if (adapter != null) {
            adapter.registerDataSetObserver(mObserver);
            mRecycler.setViewTypeCount(getAdapterViewTypeCount());
            mHasStableIds = adapter.hasStableIds();
        } else {
            mHasStableIds = false;
        }
        //TODO:
//        populate(adapter!=null);
    }

    /**
     * Clear all state because the grid will be used for a completely different set of data.
     */
    private void clearAllState() {
        // Clear all grid items and views
        mGridItems.clear();
        mPosRects.clear();
        removeAllViews();

        // Clear recycler because there could be different view types now
        mRecycler.clear();
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        return new LayoutParams(lp);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams lp) {
        return lp instanceof LayoutParams;
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    public static class LayoutParams extends ViewGroup.LayoutParams {

        /**
         * Item position this view represents
         */
        int position;

        /**
         * Type of this view as reported by the adapter
         */
        int viewType;

        /**
         * The stable ID of the item this view displays
         */
        long id = -1;

        public LayoutParams(int height) {
            super(MATCH_PARENT, height);

            if (this.height == MATCH_PARENT) {
                Log.w(TAG, "Constructing LayoutParams with height FILL_PARENT - " +
                        "impossible! Falling back to WRAP_CONTENT");
                this.height = WRAP_CONTENT;
            }
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);

            if (this.width != MATCH_PARENT) {
                Log.w(TAG, "Inflation setting LayoutParams width to " + this.width +
                        " - must be MATCH_PARENT");
                this.width = MATCH_PARENT;
            }
            if (this.height == MATCH_PARENT) {
                Log.w(TAG, "Inflation setting LayoutParams height to MATCH_PARENT - " +
                        "impossible! Falling back to WRAP_CONTENT");
                this.height = WRAP_CONTENT;
            }
        }

        public LayoutParams(ViewGroup.LayoutParams other) {
            super(other);

            if (this.width != MATCH_PARENT) {
                Log.w(TAG, "Constructing LayoutParams with width " + this.width +
                        " - must be MATCH_PARENT");
                this.width = MATCH_PARENT;
            }
            if (this.height == MATCH_PARENT) {
                Log.w(TAG, "Constructing LayoutParams with height MATCH_PARENT - " +
                        "impossible! Falling back to WRAP_CONTENT");
                this.height = WRAP_CONTENT;
            }
        }
    }

    private class RecycleBin {
        private ArrayList<View>[] mScrapViews;
        private int mViewTypeCount;
        private int mMaxScrap;

        private SparseArray<View> mTransientStateViews;

        public void setViewTypeCount(int viewTypeCount) {
            if (viewTypeCount < 1) {
                throw new IllegalArgumentException("Must have at least one view type (" +
                        viewTypeCount + " types reported)");
            }
            if (viewTypeCount == mViewTypeCount) {
                return;
            }

            @SuppressWarnings("unchecked")
            ArrayList<View>[] scrapViews = new ArrayList[viewTypeCount];

            for (int i = 0; i < viewTypeCount; i++) {
                scrapViews[i] = new ArrayList<View>();
            }
            mViewTypeCount = viewTypeCount;
            mScrapViews = scrapViews;
        }

        public void clear() {
            final int typeCount = mViewTypeCount;
            for (int i = 0; i < typeCount; i++) {
                mScrapViews[i].clear();
            }
            if (mTransientStateViews != null) {
                mTransientStateViews.clear();
            }
        }

        public void clearTransientViews() {
            if (mTransientStateViews != null) {
                mTransientStateViews.clear();
            }
        }

        public void addScrap(View v) {
            final LayoutParams lp = (LayoutParams) v.getLayoutParams();
            if (ViewCompat.hasTransientState(v)) {
                if (mTransientStateViews == null) {
                    mTransientStateViews = new SparseArray<View>();
                }
                mTransientStateViews.put(lp.position, v);
                return;
            }

            final int childCount = getChildCount();
            if (childCount > mMaxScrap) {
                mMaxScrap = childCount;
            }

            ArrayList<View> scrap = mScrapViews[lp.viewType];
            if (scrap.size() < mMaxScrap) {
                scrap.add(v);
            }
        }

        public View getTransientStateView(int position) {
            if (mTransientStateViews == null) {
                return null;
            }

            final View result = mTransientStateViews.get(position);
            if (result != null) {
                mTransientStateViews.remove(position);
            }
            return result;
        }

        public View getScrapView(int type) {
            ArrayList<View> scrap = mScrapViews[type];
            if (scrap.isEmpty()) {
                return null;
            }

            final int index = scrap.size() - 1;
            final View result = scrap.get(index);
            scrap.remove(index);
            return result;
        }
    }

    private class AdapterDataSetObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            mDataChanged = true;
            mItemCount = mAdapter.getCount();

            // TODO: Consider matching these back up if we have stable IDs.
            mRecycler.clearTransientViews();

            if (!mHasStableIds) {
                // Clear all layout records and recycle the views
                mGridItems.clear();

                recycleAllViews();

                mPosRects.clear();
            }

            // TODO: consider repopulating in a deferred runnable instead
            // (so that successive changes may still be batched)
            requestLayout();
        }

        @Override
        public void onInvalidated() {
        }
    }

    static class ColMap implements Parcelable {
        private ArrayList<Integer> values;
        int tempMap[];

        public ColMap(ArrayList<Integer> values){
            this.values = values;
        }

        private ColMap(Parcel in) {
            in.readIntArray(tempMap);
            values = new ArrayList<Integer>();
            for (int index = 0; index < tempMap.length; index++) {
                values.add(tempMap[index]);
            }
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            tempMap = toIntArray(values);
            out.writeIntArray(tempMap);
        }

        public static final Creator<ColMap> CREATOR = new Creator<ColMap>() {
            public ColMap createFromParcel(Parcel source) {
                return new ColMap(source);
            }

            public ColMap[] newArray(int size) {
                return new ColMap[size];
            }
        };

        int[] toIntArray(ArrayList<Integer> list) {
            int[] ret = new int[list.size()];
            for (int i = 0; i < ret.length; i++)
                ret[i] = list.get(i);
            return ret;
        }

        @Override
        public int describeContents() {
            return 0;
        }
    }

    static class SavedState extends BaseSavedState {
        long firstId = -1;
        int position;
        int topOffsets[];
        ArrayList<ColMap> mapping;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            firstId = in.readLong();
            position = in.readInt();
            in.readIntArray(topOffsets);
            in.readTypedList(mapping, ColMap.CREATOR);

        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeLong(firstId);
            out.writeInt(position);
            out.writeIntArray(topOffsets);
            out.writeTypedList(mapping);
        }

        @Override
        public String toString() {
            return "StaggereGridView.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + " firstId=" + firstId
                    + " position=" + position + "}";
        }

        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    /**
     * A base class for Runnables that will check that their view is still attached to
     * the original window as when the Runnable was created.
     *
     */
    private class WindowRunnnable {
        private int mOriginalAttachCount;

        public void rememberWindowAttachCount() {
            mOriginalAttachCount = getWindowAttachCount();
        }

        public boolean sameWindow() {
            return hasWindowFocus() && getWindowAttachCount() == mOriginalAttachCount;
        }
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        // If the child view is enabled then do the default behavior.
        if (mIsChildViewEnabled) {
            // Common case
            return super.onCreateDrawableState(extraSpace);
        }

        // The selector uses this View's drawable state. The selected child view
        // is disabled, so we need to remove the enabled state from the drawable
        // states.
        final int enabledState = ENABLED_STATE_SET[0];

        // If we don't have any extra space, it will return one of the static state arrays,
        // and clearing the enabled state on those arrays is a bad thing!  If we specify
        // we need extra space, it will create+copy into a new array that safely mutable.
        int[] state = super.onCreateDrawableState(extraSpace + 1);
        int enabledPos = -1;
        for (int i = state.length - 1; i >= 0; i--) {
            if (state[i] == enabledState) {
                enabledPos = i;
                break;
            }
        }

        // Remove the enabled state
        if (enabledPos >= 0) {
            System.arraycopy(state, enabledPos + 1, state, enabledPos,
                    state.length - enabledPos - 1);
        }

        return state;
    }

    final class CheckForTap implements Runnable {
        public void run() {
            if (mTouchMode == TOUCH_MODE_DOWN) {

                mTouchMode = TOUCH_MODE_TAP;
//                final View child = getChildAt(mMotionPosition - mFirstPosition);
                final View child = getChildAt(mMotionPosition);
                if (child != null && !child.hasFocusable()) {

                    if (!mDataChanged) {
                        child.setSelected(true);
                        child.setPressed(true);

                        setPressed(true);
                        //TODO:
//                        layoutChildren(true);
                        refreshDrawableState();

                        final int longPressTimeout = ViewConfiguration.getLongPressTimeout();
                        final boolean longClickable = isLongClickable();

                        if (longClickable) {
                            if (mPendingCheckForLongPress == null) {
                                mPendingCheckForLongPress = new CheckForLongPress();
                            }
                            mPendingCheckForLongPress.rememberWindowAttachCount();
                            postDelayed(mPendingCheckForLongPress, longPressTimeout);
                        } else {
                            mTouchMode = TOUCH_MODE_DONE_WAITING;
                        }

                        postInvalidate();
                    } else {
                        mTouchMode = TOUCH_MODE_DONE_WAITING;
                    }
                }
            }
        }
    }

    private class CheckForLongPress extends WindowRunnnable implements Runnable {
        public void run() {
            final int motionPosition = mMotionPosition;
//            final View child = getChildAt(motionPosition - mFirstPosition);
            final View child = getChildAt(motionPosition);
            if (child != null) {
                final int longPressPosition = mMotionPosition;
                final long longPressId = mAdapter.getItemId(mMotionPosition);

                boolean handled = false;
                if (sameWindow() && !mDataChanged) {
                    handled = performLongPress(child, longPressPosition, longPressId);
                }
                if (handled) {
                    mTouchMode = TOUCH_MODE_REST;
                    setPressed(false);
                    child.setPressed(false);
                } else {
                    mTouchMode = TOUCH_MODE_DONE_WAITING;
                }
            }
        }
    }

    private class PerformClick extends WindowRunnnable implements Runnable {
        int mClickMotionPosition;

        public void run() {
            // The data has changed since we posted this action in the event queue,
            // bail out before bad things happen
            if (mDataChanged) return;

            final ListAdapter adapter = mAdapter;
            final int motionPosition = mClickMotionPosition;
            if (adapter != null && mItemCount > 0 &&
                    motionPosition != INVALID_POSITION &&
                    motionPosition < adapter.getCount() && sameWindow()) {
//                final View view = getChildAt(motionPosition - mFirstPosition);
                final View view = getChildAt(motionPosition);
                // If there is no view, something bad happened (the view scrolled off the
                // screen, etc.) and we should cancel the click
                if (view != null) {
                    performItemClick(view, motionPosition, adapter.getItemId(motionPosition));
                }
            }
        }
    }

    public boolean performItemClick(View view, int position, long id) {
        if (mOnItemClickListener != null) {
            playSoundEffect(SoundEffectConstants.CLICK);
            if (view != null) {
                view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
            }
            mOnItemClickListener.onItemClick(this, view, position, id);
            return true;
        }

        return false;
    }

    boolean performLongPress(final View child,
                             final int longPressPosition, final long longPressId) {

        // TODO : add check for multiple choice mode.. currently modes are yet to be supported

        boolean handled = false;
        if (mOnItemLongClickListener != null) {
            handled = mOnItemLongClickListener.onItemLongClick(this, child, longPressPosition, longPressId);
        }
        if (!handled) {
            mContextMenuInfo = createContextMenuInfo(child, longPressPosition, longPressId);
            handled = super.showContextMenuForChild(this);
        }
        if (handled) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
        return handled;
    }

    @Override
    protected ContextMenuInfo getContextMenuInfo() {
        return mContextMenuInfo;
    }

    /**
     * Creates the ContextMenuInfo returned from {@link #getContextMenuInfo()}. This
     * methods knows the view, position and ID of the item that received the
     * long press.
     *
     * @param view The view that received the long press.
     * @param position The position of the item that received the long press.
     * @param id The ID of the item that received the long press.
     * @return The extra information that should be returned by
     *         {@link #getContextMenuInfo()}.
     */
    ContextMenuInfo createContextMenuInfo(View view, int position, long id) {
        return new AdapterContextMenuInfo(view, position, id);
    }

    /**
     * Extra menu information provided to the
     * {@link android.view.View.OnCreateContextMenuListener#onCreateContextMenu(android.view.ContextMenu, android.view.View, android.view.ContextMenu.ContextMenuInfo) }
     * callback when a context menu is brought up for this AdapterView.
     *
     */
    public static class AdapterContextMenuInfo implements ContextMenuInfo {

        public AdapterContextMenuInfo(View targetView, int position, long id) {
            this.targetView = targetView;
            this.position = position;
            this.id = id;
        }

        /**
         * The child view for which the context menu is being displayed. This
         * will be one of the children of this AdapterView.
         */
        public View targetView;

        /**
         * The position in the adapter for which the context menu is being
         * displayed.
         */
        public int position;

        /**
         * The row id of the item for which the context menu is being displayed.
         */
        public long id;
    }

    /**
     * @return True if the current touch mode requires that we draw the selector in the pressed
     *         state.
     */
    boolean touchModeDrawsInPressedState() {
        // FIXME use isPressed for this
        switch (mTouchMode) {
            case TOUCH_MODE_TAP:
            case TOUCH_MODE_DONE_WAITING:
                return true;
            default:
                return false;
        }
    }

    /**
     * Register a callback to be invoked when an item in this AdapterView has
     * been clicked.
     *
     * @param listener The callback that will be invoked.
     */
    public void setOnItemClickListener(OnItemClickListener listener) {
        mOnItemClickListener = listener;
    }

    /**
     * @return The callback to be invoked with an item in this AdapterView has
     *         been clicked, or null id no callback has been set.
     */
    public final OnItemClickListener getOnItemClickListener() {
        return mOnItemClickListener;
    }

    public interface OnItemClickListener {

        /**
         * Callback method to be invoked when an item in this AdapterView has
         * been clicked.
         * <p>
         * Implementers can call getItemAtPosition(position) if they need
         * to access the data associated with the selected item.
         *
         * @param parent The AdapterView where the click happened.
         * @param view The view within the AdapterView that was clicked (this
         *            will be a view provided by the adapter)
         * @param position The position of the view in the adapter.
         * @param id The row id of the item that was clicked.
         */
        void onItemClick(StaggeredGridView parent, View view, int position, long id);
    }

    /**
     * Register a callback to be invoked when an item in this AdapterView has
     * been clicked and held
     *
     * @param listener The callback that will run
     */
    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        if (!isLongClickable()) {
            setLongClickable(true);
        }
        mOnItemLongClickListener = listener;
    }

    /**
     * @return The callback to be invoked with an item in this AdapterView has
     *         been clicked and held, or null id no callback as been set.
     */
    public final OnItemLongClickListener getOnItemLongClickListener() {
        return mOnItemLongClickListener;
    }

    public interface OnItemLongClickListener {
        /**
         * Callback method to be invoked when an item in this view has been
         * clicked and held.
         *
         * Implementers can call getItemAtPosition(position) if they need to access
         * the data associated with the selected item.
         *
         * @param parent The AbsListView where the click happened
         * @param view The view within the AbsListView that was clicked
         * @param position The position of the view in the list
         * @param id The row id of the item that was clicked
         *
         * @return true if the callback consumed the long click, false otherwise
         */
        boolean onItemLongClick(StaggeredGridView parent, View view, int position, long id);
    }

    /**
     * Maps a point to a position in the list.
     *
     * @param x X in local coordinate
     * @param y Y in local coordinate
     * @return The position of the item which contains the specified point, or
     *         {@link #INVALID_POSITION} if the point does not intersect an item.
     */
    public int pointToPosition(int x, int y) {
        Rect frame = mTouchFrame;
        if (frame == null) {
            mTouchFrame = new Rect();
            frame = mTouchFrame;
        }

        final int count = getChildCount();
        for (int i = count - 1; i >= 0; i--) {
            final View child = getChildAt(i);
            if (child.getVisibility() == View.VISIBLE) {
                child.getHitRect(frame);
                if (frame.contains(x, y)) {
                    GridItem item = (GridItem) child.getTag(R.string.GRID_ITEM_TAG);
                    return item.position;
                }
            }
        }
        return INVALID_POSITION;
    }

}