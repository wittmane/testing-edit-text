/*
 * Copyright (C) 2024 Eli Wittman
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

package com.wittmane.testingedittext.widget;

import android.content.Context;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.widget.DraggableGroupedListAdapter.ListItemBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for a {@link ListView} to allow dragging to reorder objects in the list. Items can be
 * moved by using a drag handle or a long press.
 * @param <TGroup> Object type for the group entries.
 * @param <TItem> Object type for the items in groups.
 */
public class DraggableGroupedListAdapter<TGroup, TItem> extends DraggableListAdapterBase<
        DraggableGroupedListAdapter<TGroup, TItem>.GroupData,
        ListItemBuilder<TGroup, TItem>> {
    private static final String TAG = DraggableGroupedListAdapter.class.getSimpleName();
    private static final int NO_FIELD_INDEX = -1;

    private boolean mAreGroupsExpanded = false;

    /**
     * Create a {@link DraggableGroupedListAdapter} using the default view, which uses
     * {@link Object#toString()} to display each item in the list.
     * @param context The current context.
     */
    public DraggableGroupedListAdapter(Context context) {
        this(context, new ListItemBuilder<TGroup, TItem>() {
            @Override
            public void populateView(View view, TGroup group, TItem item) {
                TextView titleView = view.findViewById(R.id.title);
                titleView.setText(item != null ? item.toString() : group.toString());
            }
        });
    }

    /**
     * Create a {@link DraggableGroupedListAdapter} using the default view, which uses the specified
     * builder to allow customizing the display for each item in the list. Note that
     * {@link R.id#title} is the {@link TextView} to use for the display of the item in the list.
     * @param context The current context.
     * @param listItemBuilder The builder to update the display for each item in the list.
     */
    public DraggableGroupedListAdapter(Context context,
                                       ListItemBuilder<TGroup, TItem> listItemBuilder) {
        super(context, listItemBuilder);
    }

    /**
     * Create a {@link DraggableGroupedListAdapter} using a custom view
     * @param context The current context.
     * @param listItemResourceId The resource ID for the list item layout.
     * @param dragHandleResourceId The ID for the drag handle in the list item layout if it exists.
     * @param listItemBuilder The builder to update the display for each item in the list.
     */
    public DraggableGroupedListAdapter(Context context, int listItemResourceId,
                                       int dragHandleResourceId,
                                       ListItemBuilder<TGroup, TItem> listItemBuilder) {
        super(context, listItemResourceId, dragHandleResourceId, listItemBuilder);
    }

    public void expandGroups(boolean expand) {
        mAreGroupsExpanded = expand;
        notifyDataSetChanged();
    }

    private GroupedIndex getGroupedIndex(int flatListIndex) {
        if (mAreGroupsExpanded) {
            int groupIndex = 0;
            int itemIndex = flatListIndex - 1;
            while (groupIndex + 1 < mObjects.size()
                    && itemIndex >= mObjects.get(groupIndex).mItems.size()) {
                itemIndex -= mObjects.get(groupIndex).mItems.size() + 1;
                groupIndex++;
            }
            return new GroupedIndex(groupIndex, itemIndex);
        }
        return new GroupedIndex(flatListIndex, NO_FIELD_INDEX);
    }

    @Override
    public int getCount() {
        if (mAreGroupsExpanded) {
            //TODO: (EW) consider tracking this as groups and items are added to avoid needing to
            // recalculate
            int count = mObjects.size();
            for (GroupData group : mObjects) {
                count += group.mItems.size();
            }
            return count;
        }
        return mObjects.size();
    }

    @Override
    public Object getItem(int position) {
        GroupedIndex index = getGroupedIndex(position);
        if (index.mItemIndex == NO_FIELD_INDEX) {
            return mObjects.get(index.mGroupIndex).mGroup;
        }
        return mObjects.get(index.mGroupIndex).mItems.get(index.mItemIndex);
    }

    /**
     * Add a group to the end of the list.
     * @param row The object to add.
     */
    public void addGroup(TGroup row) {
        mObjects.add(new GroupData(row));
        notifyDataSetChanged();
    }

    /**
     * Add an item to the end of a group.
     * @param row The object to add.
     */
    public void addItem(int groupIndex, TItem row) {
        mObjects.get(groupIndex).mItems.add(row);
        notifyDataSetChanged();
    }

    public int getGroupCount() {
        return mObjects.size();
    }

    public TGroup getGroup(int groupIndex) {
        return mObjects.get(groupIndex).mGroup;
    }

    public int getItemCount(int groupIndex) {
        return mObjects.get(groupIndex).mItems.size();
    }

    public TItem getItem(int groupIndex, int itemIndex) {
        return mObjects.get(groupIndex).mItems.get(itemIndex);
    }

    @Override
    public void move(int fromIndex, int toIndex) {
        if (fromIndex == toIndex) {
            return;
        }
        if (mAreGroupsExpanded) {
            GroupedIndex fromGroupedIndex = getGroupedIndex(fromIndex);
            GroupedIndex toGroupedIndex = getGroupedIndex(toIndex);
            if (fromGroupedIndex.mItemIndex == NO_FIELD_INDEX) {
                // groups shouldn't be moving when they're expanded
                return;
            }
            GroupData fromGroup = mObjects.get(fromGroupedIndex.mGroupIndex);
            TItem item = fromGroup.mItems.get(fromGroupedIndex.mItemIndex);
            fromGroup.mItems.remove(fromGroupedIndex.mItemIndex);
            int toGroupIndex;
            int toItemIndex;
            if (toGroupedIndex.mItemIndex == NO_FIELD_INDEX) {
                if (toGroupedIndex.mGroupIndex > 0 && fromIndex > toIndex) {
                    // move to the end of the previous group
                    toGroupIndex = toGroupedIndex.mGroupIndex - 1;
                    toItemIndex = mObjects.get(toGroupIndex).mItems.size();
                } else {
                    // move to the beginning of the group
                    toGroupIndex = toGroupedIndex.mGroupIndex;
                    toItemIndex = 0;
                }
            } else {
                toGroupIndex = toGroupedIndex.mGroupIndex;
                if (fromGroupedIndex.mGroupIndex < toGroupedIndex.mGroupIndex) {
                    // since we're dragging from a list above, the items will all shift up, so this
                    // needs to be placed after the item that is being dragged over
                    toItemIndex = toGroupedIndex.mItemIndex + 1;
                } else {
                    // if we're moving to the same list, removing the item will shift the others to
                    // allow placing this back in the desired spot. if moving to a list above, we're
                    // already inserting at (above) the selected position, so we don't need any
                    // adjustments.
                    toItemIndex = toGroupedIndex.mItemIndex;
                }
            }
            mObjects.get(toGroupIndex).mItems.add(toItemIndex, item);
        } else {
            GroupData group = mObjects.get(fromIndex);
            mObjects.remove(fromIndex);
            mObjects.add(toIndex, group);
        }
        notifyDataSetChanged();
    }

    @Override
    protected void startDragDrop(View view, int position, int itemTouchX, int itemTouchY) {
        if (!canDrag(getGroupedIndex(position))) {
            return;
        }
        super.startDragDrop(view, position, itemTouchX, itemTouchY);
    }

    private boolean canDrag(GroupedIndex groupedIndex) {
        return groupedIndex.mItemIndex != NO_FIELD_INDEX || !mAreGroupsExpanded;
    }

    @Override
    protected void updateView(View view, int index) {
        GroupedIndex groupedIndex = getGroupedIndex(index);
        GroupData group = mObjects.get(groupedIndex.mGroupIndex);
        view.findViewById(R.id.dragHandle).setVisibility(
                canDrag(groupedIndex) ? View.VISIBLE : View.GONE);
        mListItemBuilder.populateView(view, group.mGroup,
                groupedIndex.mItemIndex == NO_FIELD_INDEX
                        ? null
                        : group.mItems.get(groupedIndex.mItemIndex));
    }

    @Override
    protected void updateViewsForDrag(int currentDropHoverIndex) {
        if (mAreGroupsExpanded && currentDropHoverIndex == 0) {
            // can't drop before the first group, so don't make it look like that's possible
            return;
        }
        super.updateViewsForDrag(currentDropHoverIndex);
    }

    /**
     * Interface to allow specifying how a view for an item in a list should display the matching
     * object.
     * @param <TGroup> Object type for the group entries.
     * @param <TItem> Object type for the items in groups.
     */
    public interface ListItemBuilder<TGroup, TItem> {
        /**
         * Configure a view (created from the {@link DraggableGroupedListAdapter}) to display a
         * particular object in the list.
         * @param view The view to display the item.
         * @param group The group object in the list to display with the view.
         * @param item The group's child object in the list to display with the view.
         */
        void populateView(View view, TGroup group, TItem item);
    }

    class GroupData {
        final TGroup mGroup;
        final List<TItem> mItems;
        public GroupData(TGroup group) {
            mGroup = group;
            mItems = new ArrayList<>();
        }
    }

    private static class GroupedIndex {
        final int mGroupIndex;
        final int mItemIndex;
        public GroupedIndex(int groupIndex, int itemIndex) {
            mGroupIndex = groupIndex;
            mItemIndex = itemIndex;
        }
    }
}
