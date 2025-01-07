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

package com.wittmane.testingedittext;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.wittmane.testingedittext.settings.Settings;

import java.util.Set;

public abstract class ThemedActivity extends Activity {
    // Note that if using AppCompatActivity instead of Activity on versions earlier than Lollipop,
    // the built-in EditText will look different from this custom one by being styled more like
    // modern versions (custom colored cursor, controllers, and bottom line, thicker cursor,
    // straight line bottom bar, and gray hint text). Based on digging through the code, this seems
    // to be because AppCompatViewInflater#createView injects AppCompatEditText in the place of a
    // defined EditText. AppCompatEditText uses a TintContextWrapper, which automatically recolors
    // the cursor and controllers' drawables (R.drawable.abc_text_cursor_material,
    // R.drawable.abc_text_select_handle_left_mtrl, R.drawable.abc_text_select_handle_middle_mtrl,
    // and R.drawable.abc_text_select_handle_right_mtrl) (see AppCompatDrawableManager).
    // Interestingly, AppCompatViewInflater looks for "EditText" to be the tag in the xml, so
    // specifying "android.widget.EditText" wouldn't get replaced. It seems that there is no way to
    // automatically tie this custom copy of the EditText into the same tint handling. If we want
    // that, we'd have to add custom handling around loading the drawables, which would deviate from
    // the AOSP version that this copies from, and it would force this custom EditText to be used
    // with AppCompat, so in order to keep it more generic, we'll skip that and just style to match
    // the android version, rather than have a consistent view between versions of this app. I
    // didn't look into the hint color much, but it also seems to be coming from the replaced
    // EditText.

    private static final String TAG = ThemedActivity.class.getSimpleName();

    private int mThemeId = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? Resources.ID_NULL : 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Settings.init(this);
        mThemeId = Settings.getThemeId(this);
        setTheme(mThemeId);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        int themeId = Settings.getThemeId(this);
        recreateActivityOnThemeChange(this, themeId, mThemeId);
        super.onResume();
    }

    public static void recreateActivityOnThemeChange(Activity activity,
                                                     int oldThemeId, int newThemeId) {
        if (oldThemeId == newThemeId) {
            return;
        }

        if (Settings.isHoloTheme(oldThemeId) == Settings.isHoloTheme(newThemeId)) {
            activity.recreate();
        } else {
            //TODO: (EW) calling recreate causes a ClassCastException to be thrown when restoring
            // instance state because the holo theme uses com.android.internal.widget.ActionBarView
            // and the material theme uses android.widget.Toolbar and for some reason it tries to
            // restore the saved state as if it came from the same type of object. find some better
            // way of managing this, but for now, we'll just close the activity and manually restart
            // it with the same intent that originally created it, but loses any state, including
            // the fragment back stack. the error could also be prevented by not calling
            // super.onRestoreInstanceState when recreating the activity.
            activity.finish();
            // create a custom copy of the intent (ie not using Intent's copy constructor) to avoid
            // getting flagged as launching an unsafe intent.
            Intent intent = copy(activity.getIntent());
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            activity.startActivity(intent);
        }
    }

    private static Intent copy(Intent source) {
        Intent target = new Intent();
        target.setAction(source.getAction());
        target.setDataAndType(source.getData(), source.getType());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            target.setIdentifier(source.getIdentifier());
        }
        target.setPackage(source.getPackage());
        target.setComponent(source.getComponent());
        target.setComponent(source.getComponent());
        Set<String> categories = source.getCategories();
        if (categories != null) {
            for (String category : categories) {
                target.addCategory(category);
            }
        }
        target.setFlags(source.getFlags());
        Rect sourceBounds = source.getSourceBounds();
        if (sourceBounds != null) {
            target.setSourceBounds(new Rect(sourceBounds));
        }
        Intent selector = source.getSelector();
        if (selector != null) {
            target.setSelector(copy(source.getSelector()));
        }
        Bundle extras = source.getExtras();
        if (extras != null) {
            target.putExtras(new Bundle(extras));
        }
        ClipData clipData = source.getClipData();
        if (clipData != null) {
            target.setClipData(new ClipData(clipData));
        }
        return target;
    }
}
