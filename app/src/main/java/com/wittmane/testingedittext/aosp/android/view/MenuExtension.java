/*
 * Copyright (C) 2025 Eli Wittman
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

package com.wittmane.testingedittext.aosp.android.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

import androidx.annotation.RequiresApi;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;

/**
 * (EW) content from {@link Menu} that is blocked from apps accessing
 */
public class MenuExtension {

    // (EW) the AOSP version (added in Android 14) is marked as hidden. made static to allow calling
    // on any Menu. this is a hacky solution to try to call the AOSP version and won't always work,
    // but it seems to be the best we can do.
    //TODO: (EW) I'm hoping that in future versions this API or something similar will be properly
    // exposed. see if there is something better we can do in future versions.
    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static void setOptionalIconsVisible(Menu menu, Context context, boolean visible) {
        // (EW) this was blocked from reflection when I tested it, but in case that ever changes, we
        // can still try since this would be a better solution than the hack below.
        try {
            Method setOptionalIconsVisibleMethod =
                    Menu.class.getMethod("setOptionalIconsVisible", boolean.class);
            setOptionalIconsVisibleMethod.invoke(menu, true);
            return;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) { }

        if (!(menu instanceof ContextMenu)) {
            // (EW) this hack only works with ContextMenu
            return;
        }
        if (!visible) {
            // (EW) this hack only works to make the icons visible. they are not visible by default,
            // so not being able to disable it shouldn't be a real problem.
            return;
        }

        // (EW) in Android 14 and 15 if AppGlobals#getIntCoreSetting was true for
        // TextFlags.KEY_ENABLE_NEW_CONTEXT_MENU and always starting in Android 16,
        // EditText#onCreateContextMenu eventually calls into Menu#setOptionalIconsVisible(true), so
        // we'll just call into that to get the method called. first we'll need to copy out the
        // existing items to remove anything added from the EditText call at the end.

        HashSet<MenuItem> originalItems = new HashSet<>();
        for (int i = 0; i < menu.size(); i++) {
            originalItems.add(menu.getItem(i));
        }

        DummyEditText editText = new DummyEditText(context);
        // (EW) trigger the Editor to be created
        editText.setText("");
        try {
            // (EW) from testing, this throws a NullPointerException due to not having a parent when
            // calling into the super method, which probably is actually good since we're only
            // calling this to call Editor#setContextMenuAnchor to prevent quiting before calling
            // Menu#setOptionalIconsVisible.
            editText.showContextMenu(0, 0);
        } catch (Exception e) { }
        editText.onCreateContextMenu((ContextMenu) menu);

        for (int i = menu.size() - 1; i >= 0; i--) {
            if (!originalItems.contains(menu.getItem(i))) {
                menu.removeItem(menu.getItem(i).getItemId());
            }
        }
    }

    // (EW) custom EditText class to force it to create a context menu (technically add content to a
    // specified menu) specifically to get it to call Menu#setOptionalIconsVisible(true) on the menu
    @SuppressLint("AppCompatCustomView")
    private static class DummyEditText extends EditText {
        public DummyEditText(Context context) {
            super(context);
        }

        // (EW) overridden to make public to allow calling
        @Override
        public void onCreateContextMenu(ContextMenu menu) {
            super.onCreateContextMenu(menu);
        }

        // (EW) Editor#onCreateContextMenu quits without building the menu if this returns -1, so
        // force it to never do that
        @Override
        public int getOffsetForPosition(float x, float y) {
            return 0;
        }
    }

}
