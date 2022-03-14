package com.wittmane.testingedittext;

import android.app.Activity;
import android.os.Bundle;

public class MainActivity extends Activity {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}