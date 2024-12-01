/*
 * Copyright (C) 2022-2024 Eli Wittman
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
import com.wittmane.testingedittext.widget.DraggableListAdapter.ListItemBuilder;

/**
 * Adapter for a {@link ListView} to allow dragging to reorder objects in the list. Items can be
 * moved by using a drag handle or a long press.
 * @param <T> Object type for the list.
 */
public class DraggableListAdapter<T> extends DraggableListAdapterBase<T, ListItemBuilder<T>> {

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
        super(context, listItemBuilder);
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
        super(context, listItemResourceId, dragHandleResourceId, listItemBuilder);
    }

    @Override
    public int getCount() {
        return mObjects.size();
    }

    @Override
    public T getItem(int position) {
        return mObjects.get(position);
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

    @Override
    public void move(int fromIndex, int toIndex) {
        if (fromIndex == toIndex) {
            return;
        }
        T item = mObjects.get(fromIndex);
        mObjects.remove(fromIndex);
        mObjects.add(toIndex, item);
        notifyDataSetChanged();
    }

    @Override
    public void updateView(View view, int index) {
        mListItemBuilder.populateView(view, mObjects.get(index));
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
