package com.asosiaciondeasis.animalesdeasis.Util.Helpers;

import javafx.scene.paint.Color;

/**
 * Brand colours for the few places that need a {@link Color} object rather than
 * a stylesheet rule.
 *
 * <p><b>Why this class exists.</b> Colour for this application lives in
 * {@code css/theme.css} as looked-up colors, and every stylesheet refers to it
 * by name. Some third-party controls cannot participate in that: TilesFX takes
 * {@code Color} instances through its builder API and never consults the scene's
 * stylesheets. Without somewhere to put them, those colours end up as
 * {@code Color.web("#3498db")} scattered through controller code — invisible to
 * anyone doing a rebrand, and the exact problem the token file was created to
 * solve.</p>
 *
 * <p>These constants are the Java-side mirror of the tokens. <b>They must be
 * kept in step with {@code theme.css} by hand</b>; JavaFX offers no way to read
 * a looked-up color out of a stylesheet before a scene exists.
 * {@code ThemeTokensTest} fails the build if the two drift apart, so the
 * duplication cannot rot silently.</p>
 */
public final class BrandPalette {

    private BrandPalette() {
        // Constants only.
    }

    /** Mirrors {@code -chart-blue}. */
    public static final Color CHART_BLUE = Color.web("#3498db");

    /** Mirrors {@code -success}. */
    public static final Color SUCCESS = Color.web("#27ae60");

    /** Mirrors {@code -danger}. */
    public static final Color DANGER = Color.web("#e74c3c");

    /** Mirrors {@code -warning}. */
    public static final Color WARNING = Color.web("#f59e0b");
}
