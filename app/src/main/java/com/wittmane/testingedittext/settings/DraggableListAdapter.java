/*
 * Copyright (C) 2022 Eli Wittman
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.wittmane.testingedittext.settings;

import android.content.ClipData;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.view.DragEvent;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.DragShadowBuilder;
import android.view.View.OnDragListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.wittmane.testingedittext.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static android.view.View.DRAG_FLAG_OPAQUE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

/**
 * Adapter for a {@link ListView} to allow dragging to reorder objects in the list. Items can be
 * moved by using a drag handle or a long press.
 * @param <T> Object type for the list.
 */
public class DraggableListAdapter<T> extends BaseAdapter {
    private static final int RESOURCES_ID_NULL =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? Resources.ID_NULL : 0;
    private static final int SCROLL_TIMER_TIMEOUT = 75;
    private static final int SCROLL_SPEED = 1;

    private final List<T> mObjects = new ArrayList<>();

    private final LayoutInflater mInflater;
    private final int mListItemResourceId;
    private final int mDragHandleResourceId;
    private final ListItemBuilder<T> mListItemBuilder;
    private ListView mParent;

    /** The index of the object that is currently being dragged */
    private int mCurrentDragIndex = -1;
    /** The offset from {@link #mCurrentDragIndex} that has shifted its displayed object as a
     *  preview for the result of dropping the item at the current location of the drag hover */
    private int mCurrentShift = 0;
    /** The y position within the {@link ListView} for the touch point that initiated the drag,
     *  which should be used for to help determine whether the {@link ListView} should scroll */
    private float mDragStartParentY;

    /** The timer for incrementing the scroll position on a regular interval while the dragged view
     *  is extending past the top or bottom of the {@link ListView}. */
    private CurrentThreadTimer mScrollTimer;
    /** The current scrolling that is in progress. This should be 1 for scrolling down or -1 for
     *  scrolling up. */
    private int mCurrentScroll;
    /** The count of consecutive timer intervals hit for the scroll, which should progressively
     *  increase the speed of the scroll. */
    private int mScrollCount;

    /**
     * Create a {@link DraggableListAdapter} using the default view, which uses
     * {@link Object#toString()} to display each item in the list.
     * @param context The current context.
     */
    public DraggableListAdapter(Context context) {
        this(context, new ListItemBuilder<T>() {
            @Override
            public void populateView(View view, T item) {
                TextView titleView = view.findViewById(R.id.title);
                titleView.setText(item.toString());
            }
        });
    }

    /**
     * Create a {@link DraggableListAdapter} using the default view, which uses the specified
     * builder to allow customizing the display for each item in the list. Note that
     * {@link R.id#title} is the {@link TextView} to use for the display of the item in the list.
     * @param context The current context.
     * @param listItemBuilder The builder to update the display for each item in the list.
     */
    public DraggableListAdapter(Context context, ListItemBuilder<T> listItemBuilder) {
        this(context, R.layout.draggable_row, R.id.dragHandle, listItemBuilder);
    }

    /**
     * Create a {@link DraggableListAdapter} using a custom view
     * @param context The current context.
     * @param listItemResourceId The resource ID for the list item layout.
     * @param dragHandleResourceId The ID for the drag handle in the list item layout if it exists.
     * @param listItemBuilder The builder to update the display for each item in the list.
     */
    public DraggableListAdapter(Context context, int listItemResourceId, int dragHandleResourceId,
                                ListItemBuilder<T> listItemBuilder) {
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mListItemResourceId = listItemResourceId;
        mDragHandleResourceId = dragHandleResourceId;
        mListItemBuilder = listItemBuilder;
    }

    @Override
    public int getCount() {
        return mObjects.size();
    }

    @Override
    public T getItem(int position) {
        return mObjects.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    /**
     * Add an object to the end of the list.
     * @param row The object to add.
     */
    public void add(T row) {
        mObjects.add(row);
        notifyDataSetChanged();
    }

    /**
     * Insert an object at a specified position in the list.
     * @param index The index to insert the object.
     * @param row The object to insert into the list.
     */
    public void insert(int index, T row) {
        mObjects.add(index, row);
        notifyDataSetChanged();
    }

    /**
     * Remove an object from the list.
     * @param index The index of the object to remove.
     */
    public void remove(int index) {
        mObjects.remove(index);
        notifyDataSetChanged();
    }

    /**
     * Move an object in the list to a new location in the list.
     * @param fromIndex The index of the object to move.
     * @param toIndex The destination in the list to move the object.
     */
    public void move(int fromIndex, int toIndex) {
        if (fromIndex == toIndex) {
            return;
        }
        T item = mObjects.get(fromIndex);
        mObjects.remove(fromIndex);
        mObjects.add(toIndex, item);
        notifyDataSetChanged();
    }

    /**
     * Remove all objects from the list.
     */
    public void clear() {
        mObjects.clear();
        notifyDataSetChanged();
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        if (parent instanceof ListView && mParent != parent) {
            mParent = (ListView) parent;
            mParent.setOnDragListener(new OnDragListener() {
                @Override
                public boolean onDrag(View v, DragEvent event) {
                    // make sure the drag event is only for one of the list items and matches the
                    // item we're already tracking as dragging
                    if (!(event.getLocalState() instanceof DragLocalState)) {
                        return false;
                    }
                    DragLocalState localState = (DragLocalState)event.getLocalState();
                    if (localState.mItemIndex != mCurrentDragIndex) {
                        return false;
                    }

                    int hoverIndex;
                    switch (event.getAction()) {
                        case DragEvent.ACTION_DRAG_STARTED:
                            mDragStartParentY = event.getY();
                            return true;
                        case DragEvent.ACTION_DRAG_ENTERED:
                            // this event doesn't contain the y position so we'll need to wait for
                            // the drag location event to figure out where things need to shift for
                            // the drag hover
                            return true;
                        case DragEvent.ACTION_DRAG_EXITED:
                            resetDragShift(false);
                            return true;
                        case DragEvent.ACTION_DROP:
                            hoverIndex = getDragHoverIndex(localState, event.getY());
                            if (hoverIndex < 0) {
                                // this shouldn't happen
                                return false;
                            }
                            move(localState.mItemIndex, hoverIndex);
                            return true;
                        case DragEvent.ACTION_DRAG_ENDED:
                            resetDragShift(true);
                            stopScrollTimer();
                            return true;
                        case DragEvent.ACTION_DRAG_LOCATION:
                            float yInParent = event.getY();
                            hoverIndex = getDragHoverIndex(localState, event.getY());
                            if (hoverIndex < 0) {
                                // this shouldn't happen
                                resetDragShift(false);
                                return true;
                            }

                            // update any necessary views in case the hover position changed
                            updateViewsForDrag(hoverIndex);

                            // scroll the view if the edge of the dragged view is past the top or
                            // bottom of the list view and make sure the view is dragged past where
                            // it started in case the drag was started with the view partially cut
                            // off
                            float viewTopEdgeInParent = event.getY() - localState.mItemTouchY;
                            float viewBottomEdgeInParent =
                                    event.getY() + localState.mItemHeight - localState.mItemTouchY;
                            if (viewBottomEdgeInParent > mParent.getHeight()
                                    && yInParent > mDragStartParentY) {
                                // scroll down
                                startScrollTimer(1);
                            } else if (viewTopEdgeInParent < 0
                                    && yInParent < mDragStartParentY) {
                                // scroll up
                                startScrollTimer(-1);
                            } else {
                                stopScrollTimer();
                            }
                            return true;
                    }
                    return false;
                }
            });
        }

        View view;
        if (convertView == null) {
            view = mInflater.inflate(this.mListItemResourceId, null);
            assert view != null;
        } else {
            view = convertView;
        }
        view.setVisibility(VISIBLE);

        updateView(view, position);

        if (mDragHandleResourceId != RESOURCES_ID_NULL) {
            View dragHandle = view.findViewById(mDragHandleResourceId);
            // touching the drag handle starts a drag event
            dragHandle.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View touchView, MotionEvent motionEvent) {
                    if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                        Coordinate coordinate =
                                new Coordinate(motionEvent.getX(), motionEvent.getY());
                        if (!translateCoordinate(touchView, view, coordinate)) {
                            // this shouldn't happen
                            coordinate.mX = view.getWidth() / 2f;
                            coordinate.mY = view.getHeight() / 2f;
                        }
                        startDragDrop(view, position, (int) coordinate.mX, (int) coordinate.mY);
                        return true;
                    }
                    return false;
                }
            });
        }
        // long pressing anywhere on the item also starts a drag event
        new LongClickManager(view, new OnLongClickImprovedListener() {
            @Override
            public boolean onLongClick(View v, float x, float y) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                startDragDrop(view, position, (int) x, (int) y);
                return true;
            }
        });

        return view;
    }

    private void startDragDrop(View view, int position, int itemTouchX, int itemTouchY) {
        ClipData clipData = ClipData.newPlainText("", "");
        DragShadowBuilder dragShadow = new OffCenterDragShadowBuilder(view, itemTouchX, itemTouchY);
        DragLocalState localState = new DragLocalState(position, itemTouchY, view.getHeight());
        mCurrentDragIndex = position;
        mCurrentShift = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            view.startDragAndDrop(clipData, dragShadow, localState, DRAG_FLAG_OPAQUE);
        } else {
            view.startDrag(clipData, dragShadow, localState, 0);
        }
    }

    private void updateView(View view, int index) {
        mListItemBuilder.populateView(view, mObjects.get(index));
    }

    /**
     * Update any necessary views to hide or display the item for the adjacent row as a preview for
     * dropping the item. This essentially visually shifts the items between where the dragged item
     * came from and where the item is currently being dragged over.
     * @param currentDropHoverIndex The index that the dragged item is currently hovering over and
     *                             would move the item to if dropped.
     */
    private void updateViewsForDrag(int currentDropHoverIndex) {
        if (currentDropHoverIndex < 0 || currentDropHoverIndex >= mObjects.size()) {
            throw new IndexOutOfBoundsException(
                    "currentDropHoverIndex: " + currentDropHoverIndex
                            + ", length: " + mObjects.size());
        }
        // only update items that will actually need to update their display
        int lastShiftedIndex = mCurrentDragIndex + mCurrentShift;
        int changeStart = Math.min(lastShiftedIndex, currentDropHoverIndex);
        int changeEnd = Math.max(lastShiftedIndex, currentDropHoverIndex);
        for (int i = changeStart; i <= changeEnd; i++) {
            View view = getItemView(i);
            if (view == null) {
                // this shouldn't happen, but if we don't have the view yet, skip it
                continue;
            }
            if (!isInDragRange(mCurrentDragIndex, currentDropHoverIndex, i)) {
                // reset from a shifted and/or hidden position
                updateView(view, i);
                view.setVisibility(VISIBLE);
            } else if (i == currentDropHoverIndex) {
                updateView(view, mCurrentDragIndex);
                view.setVisibility(INVISIBLE);
            } else {
                int shift = mCurrentDragIndex < currentDropHoverIndex ? 1 : -1;
                updateView(view, i + shift);
                view.setVisibility(VISIBLE);
            }
        }
        mCurrentShift = currentDropHoverIndex - mCurrentDragIndex;
    }

    /**
     * Reset the views that are hidden or displaying a different item as a drag preview back to
     * their original states.
     * @param endDrag Whether the initially dragged item should be reset to its original state too.
     */
    private void resetDragShift(boolean endDrag) {
        if (mCurrentDragIndex < 0) {
            // not dragging - nothing to do
            return;
        }
        if (mCurrentShift != 0) {
            int direction = mCurrentShift > 0 ? 1 : -1;
            for (int offset = mCurrentShift; offset * direction > 0; offset -= direction) {
                int index = mCurrentDragIndex + offset;
                View view = getItemView(index);
                if (view == null) {
                    // nothing to reset
                    continue;
                }
                updateView(view, index);
                view.setVisibility(VISIBLE);
            }
        }
        mCurrentShift = 0;
        View dragStartView = getItemView(mCurrentDragIndex);
        // this view really shouldn't be null, but it might be possible
        if (dragStartView != null) {
            int visibility;
            if (endDrag) {
                updateView(dragStartView, mCurrentDragIndex);
                visibility = VISIBLE;
            } else {
                visibility = INVISIBLE;
            }
            dragStartView.setVisibility(visibility);
        }
        if (endDrag) {
            mCurrentDragIndex = -1;
        }
    }

    private static boolean isInDragRange(int dragStartIndex, int dragCurrentIndex,
                                         int indexToCheck) {
        return (dragStartIndex <= indexToCheck && indexToCheck <= dragCurrentIndex)
                || (dragStartIndex >= indexToCheck && indexToCheck >= dragCurrentIndex);
    }

    /**
     * Get the {@link ListView}'s child view for the list item at a specified index if that view
     * exists.
     * @param index The index for the list item to get the view of.
     * @return The view or null if there currently isn't a view for it.
     */
    private View getItemView(int index) {
        if (mParent == null) {
            return null;
        }
        final int firstItemPosition = mParent.getFirstVisiblePosition();
        final int lastItemPosition = firstItemPosition + mParent.getChildCount() - 1;
        if (index < firstItemPosition || index > lastItemPosition ) {
            // view hasn't been created yet (or may have been destroyed/repurposed)
            return null;
        } else {
            return mParent.getChildAt(index - firstItemPosition);
        }
    }

    private int getDragHoverIndex(DragLocalState localState, float eventY) {
        if (mParent == null) {
            return -1;
        }

        float draggedItemTopEdge = eventY - localState.mItemTouchY;
        float draggedItemBottomEdge = eventY + localState.mItemHeight - localState.mItemTouchY;

        // find the range of items that dragging item is hovering over since those are the extent of
        // the items that will may need to shift
        final int firstItemPosition = mParent.getFirstVisiblePosition();
        final int lastItemPosition = firstItemPosition + mParent.getChildCount() - 1;
        int potentiallyDisplacingMinIndex = firstItemPosition;
        int potentiallyDisplacingMaxIndex = lastItemPosition;
        for (int i = firstItemPosition; i <= lastItemPosition; i++) {
            View itemView = mParent.getChildAt(i - firstItemPosition);
            if (itemView.getTop() <= draggedItemTopEdge
                    && draggedItemTopEdge <= itemView.getBottom()) {
                potentiallyDisplacingMinIndex = i;
            }
            if (itemView.getTop() <= draggedItemBottomEdge
                    && draggedItemBottomEdge <= itemView.getBottom()) {
                potentiallyDisplacingMaxIndex = i;
            }
        }

        int placeholderIndex = mCurrentDragIndex + mCurrentShift;
        if (placeholderIndex < potentiallyDisplacingMaxIndex) {
            // dragging down / shifting up
            // find the furthest down item that has a center that the bottom edge of the dragging
            // item has passed (which means that there is more space above the dragging item for the
            // item to fit, so it should shift)
            for (int i = potentiallyDisplacingMaxIndex; i > potentiallyDisplacingMinIndex; i--) {
                View itemView = mParent.getChildAt(i - firstItemPosition);
                float itemCenter = (itemView.getTop() + itemView.getBottom()) / 2f;
                if (draggedItemBottomEdge > itemCenter) {
                    return i;
                }
            }
            return potentiallyDisplacingMinIndex;
        } else if (placeholderIndex > potentiallyDisplacingMinIndex) {
            // dragging up / shifting items down
            // find the furthest up item that has a center that the top edge of the dragging item
            // has passed (which means that there is more space below the dragging item for the item
            // to fit, so it should shift)
            for (int i = potentiallyDisplacingMinIndex; i < potentiallyDisplacingMaxIndex; i++) {
                View itemView = mParent.getChildAt(i - firstItemPosition);
                float itemCenter = (itemView.getTop() + itemView.getBottom()) / 2f;
                if (draggedItemTopEdge < itemCenter) {
                    return i;
                }
            }
            return potentiallyDisplacingMaxIndex;
        }
        // no movement
        return placeholderIndex;
    }

    private synchronized void startScrollTimer(int scrollDirection) {
        if (scrollDirection == 0) {
            throw new IllegalArgumentException("scroll direction can't be 0");
        }
        if ((scrollDirection > 0 && mCurrentScroll < 0)
                || (scrollDirection < 0 && mCurrentScroll > 0)) {
            // direction is changing, so any existing timer can't be reused
            stopScrollTimer();
        }
        mCurrentScroll = scrollDirection;
        if (mScrollTimer != null) {
            // the timer is already running, so there isn't anything to do
            return;
        }
        mScrollTimer = new CurrentThreadTimer();
        mScrollTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (mParent == null) {
                    return;
                }
                int scroll = SCROLL_SPEED * mCurrentScroll * mScrollCount;
                mParent.smoothScrollBy(scroll, SCROLL_TIMER_TIMEOUT);
                mScrollCount++;

                // stop the timer when there isn't anything more to scroll
                if (mCurrentScroll > 0) {
                    View view = getItemView(mObjects.size() - 1);
                    if (view != null && view.getY() + view.getHeight() <= mParent.getHeight()) {
                        stopScrollTimer();
                    }
                } else {
                    View view = getItemView(0);
                    if (view != null && view.getY() >= 0) {
                        stopScrollTimer();
                    }
                }
            }
        }, SCROLL_TIMER_TIMEOUT, SCROLL_TIMER_TIMEOUT);
    }

    private synchronized void stopScrollTimer() {
        if (mScrollTimer == null) {
            // there isn't anything to stop
            return;
        }
        mScrollTimer.cancel();
        mScrollTimer = null;
        mScrollCount = 0;
    }

    private static class DragLocalState {
        private final int mItemIndex;
        private final int mItemTouchY;
        private final int mItemHeight;
        public DragLocalState(int itemIndex, int itemTouchY, int itemHeight) {
            mItemIndex = itemIndex;
            mItemTouchY = itemTouchY;
            mItemHeight = itemHeight;
        }
    }

    /**
     * Class to support registering an improved long click listener that also includes the touch
     * point.
     */
    private static class LongClickManager {
        private Coordinate mLastTouchPoint;

        /**
         * Set up an on long click listener.
         * @param view The view to register the listener.
         * @param listener The listener to register.
         */
        public LongClickManager(View view, OnLongClickImprovedListener listener) {
            view.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    return listener.onLongClick(v, mLastTouchPoint.mX, mLastTouchPoint.mY);
                }
            });
            // track the last touch point since OnLongClickListener doesn't provide it
            view.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    mLastTouchPoint = new Coordinate(event.getX(), event.getY());
                    return false;
                }
            });
        }
    }

    /**
     * Interface definition for a callback to be invoked when a view has been clicked and held.
     */
    private interface OnLongClickImprovedListener {
        /**
         * Called when a view has been clicked and held.
         * @param v The view that was clicked and held.
         * @param x The last x position touched on the view.
         * @param y The last y position touched on the view.
         * @return true if the callback consumed the long click, false otherwise.
         */
        boolean onLongClick(View v, float x, float y);
    }

    /**
     * A timer with a callback that is executed on the same thread that the timer was created on.
     */
    private static class CurrentThreadTimer {
        private final Timer mTimer = new Timer();
        private boolean mCanceled = false;
        private final Handler mHandler = new Handler();

        /**
         * Schedule a specified task for execution after a specified delay.
         * @param task The task to be scheduled.
         * @param delay The delay in milliseconds before the task is to be executed.
         */
        public synchronized void schedule(TimerTask task, long delay) {
            mTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    mHandler.post(new Runnable() {
                        public void run() {
                            synchronized (CurrentThreadTimer.this) {
                                if (!mCanceled) {
                                    task.run();
                                }
                            }
                        }
                    });
                }
            }, delay);
        }

        public synchronized void scheduleAtFixedRate(TimerTask task, long delay, long period) {
            mTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    mHandler.post(new Runnable() {
                        public void run() {
                            synchronized (CurrentThreadTimer.this) {
                                if (!mCanceled) {
                                    task.run();
                                }
                            }
                        }
                    });
                }
            }, delay, period);
        }

        public synchronized void cancel() {
            mTimer.cancel();
            mCanceled = true;
        }
    }

    /**
     * A {@link DragShadowBuilder} that allows specifying where in the dragged object it was touched
     * to allow the drag shadow to be moved by the same position.
     */
    private static class OffCenterDragShadowBuilder extends DragShadowBuilder {
        private final int mTouchX;
        private final int mTouchY;

        public OffCenterDragShadowBuilder(View view, int touchX, int touchY) {
            super(view);
            mTouchX = touchX;
            mTouchY = touchY;
        }

        @Override
        public void onProvideShadowMetrics(Point shadowSize, Point touchPoint) {
            super.onProvideShadowMetrics(shadowSize, touchPoint);
            touchPoint.set(mTouchX, mTouchY);
        }
    }

    private static class Coordinate {
        private float mX;
        private float mY;
        public Coordinate(float x, float y) {
            mX = x;
            mY = y;
        }
    }

    /**
     * Translate a coordinate that is relative to the current view to be relative to the specified
     * ancestor view.
     * @param currentView The view that the coordinate is currently relative to.
     * @param targetAncestor The view that the coordinate should be translated to be relative to.
     * @param coordinate The coordinate to translate.
     * @return Whether the coordinate was able to be translated (whether the ancestor was found).
     */
    private static boolean translateCoordinate(View currentView, View targetAncestor,
                                               Coordinate coordinate) {
        if (currentView == targetAncestor) {
            return true;
        }
        ViewParent nextView = currentView.getParent();
        if (nextView instanceof View) {
            coordinate.mX += currentView.getX();
            coordinate.mY += currentView.getY();
            return translateCoordinate((View) nextView, targetAncestor, coordinate);
        }
        return false;
    }

    /**
     * Interface to allow specifying how a view for an item in a list should display the matching
     * object.
     * @param <T> Object type for the list item.
     */
    public interface ListItemBuilder<T> {
        /**
         * Configure a view (created from the {@link DraggableListAdapter}) to display a particular
         * object in the list.
         * @param view The view to display the item.
         * @param item The object in the list to display with the view.
         */
        void populateView(View view, T item);
    }
}
