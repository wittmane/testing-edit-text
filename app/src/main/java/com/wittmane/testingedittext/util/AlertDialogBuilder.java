/*
 * Copyright (C) 2026 Eli Wittman
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

package com.wittmane.testingedittext.util;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;

/**
 * A {@link AlertDialog.Builder} that automatically adjusts the dialog icon's color to match the
 * text and sets up predictive back handling.
 */
public class AlertDialogBuilder extends AlertDialog.Builder {
    public AlertDialogBuilder(Context context) {
        super(context);
    }

    @Override
    public AlertDialog create() {
        AlertDialog dialog = super.create();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PredictiveBackAnimationManager.setUp(dialog);
        }

        return dialog;
    }

    @Override
    public AlertDialog show() {
        AlertDialog dialog = super.show();

        IconUtils.matchIconColor(dialog);

        return dialog;
    }
}
