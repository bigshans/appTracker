package dev.aerian.apptracker.util;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Applies status/navigation bar and display-cutout safe-area padding. */
public final class WindowInsetsHelper {

    private WindowInsetsHelper() {
    }

    public static void apply(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
            view.setPadding(
                    Math.max(bars.left, cutout.left),
                    Math.max(bars.top, cutout.top),
                    Math.max(bars.right, cutout.right),
                    Math.max(bars.bottom, cutout.bottom));
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
