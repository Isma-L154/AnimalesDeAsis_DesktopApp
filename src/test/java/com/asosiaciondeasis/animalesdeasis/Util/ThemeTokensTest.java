package com.asosiaciondeasis.animalesdeasis.Util;

import com.asosiaciondeasis.animalesdeasis.Util.Helpers.BrandPalette;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the design-token system against the slow drift that produced it.
 *
 * <p>The stylesheets previously carried 411 hardcoded colour literals while
 * {@code theme.css} sat beside them claiming to be the single source of truth.
 * Nothing enforced the claim, so it quietly stopped being true. These tests are
 * that enforcement: they fail the build the moment a hex literal reappears in a
 * view stylesheet, a stylesheet refers to a token nobody defined, or the Java
 * mirror of the palette drifts from the stylesheet it mirrors.</p>
 *
 * <p>Deliberately plain file parsing rather than JavaFX CSS machinery: these
 * must run on a headless CI agent with no display, and they must report the
 * offending file and line rather than a rendering difference.</p>
 */
class ThemeTokensTest {

    private static final Path CSS_ROOT = Paths.get("src", "main", "resources", "css");
    private static final Path THEME = CSS_ROOT.resolve("theme.css");

    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{3,8}");
    /**
     * A token declaration, e.g. `    -brand-primary: #f2921d;`.
     *
     * <p>Indentation is not part of the pattern. Tying it to exactly four spaces
     * would mean a reformat silently narrowed what the tests can see — and a test
     * that stops looking without saying so is worse than no test. What identifies
     * a declaration is its shape: a custom property name, which is any leading
     * hyphen not followed by {@code fx-}, since those are JavaFX's own properties.</p>
     */
    private static final Pattern DECLARATION =
            Pattern.compile("^\\s*(-(?!fx-)[a-z0-9-]+)\\s*:\\s*([^;]+);", Pattern.MULTILINE);
    /**
     * An rgba() literal with unequal channels. Equal channels are black, white or
     * grey — legitimate neutral shadows. Unequal ones are a brand or semantic
     * colour written in a notation the hex sweep cannot see, which is exactly how
     * 43 of them survived the first pass.
     */
    private static final Pattern RGBA = Pattern.compile(
            "rgba\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*[0-9.]+\\s*\\)");
    /** A token reference in a view stylesheet, e.g. `-fx-text-fill: -brand-text;` */
    private static final Pattern REFERENCE = Pattern.compile(
            "(?<![\\w-])(-(?:brand|surface|flat|slate|grey|success|danger|warning|chart|text|border|focus)[a-z0-9-]*)");

    private static List<Path> viewStylesheets() throws IOException {
        try (Stream<Path> paths = Files.walk(CSS_ROOT)) {
            return paths.filter(p -> p.toString().endsWith(".css"))
                        .filter(p -> !p.getFileName().toString().equals("theme.css"))
                        .sorted()
                        .toList();
        }
    }

    private static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    /** Token name to declared value, taken from the `.root` block of theme.css. */
    private static Map<String, String> declaredTokens() throws IOException {
        Map<String, String> tokens = new HashMap<>();
        Matcher m = DECLARATION.matcher(read(THEME));
        while (m.find()) {
            tokens.put(m.group(1), m.group(2).trim());
        }
        return tokens;
    }

    @Test
    @DisplayName("no view stylesheet contains a colour literal")
    void viewStylesheetsCarryNoHexLiterals() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path css : viewStylesheets()) {
            String[] lines = read(css).split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                Matcher m = HEX.matcher(lines[i]);
                while (m.find()) {
                    offenders.add(css + ":" + (i + 1) + "  " + m.group());
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "Colour literals belong in theme.css as a token, not in a view stylesheet. Found:\n  "
                        + String.join("\n  ", offenders));
    }

    @Test
    @DisplayName("no view stylesheet contains a chromatic rgba literal")
    void viewStylesheetsCarryNoChromaticRgba() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path css : viewStylesheets()) {
            String[] lines = read(css).split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                Matcher m = RGBA.matcher(lines[i]);
                while (m.find()) {
                    int r = Integer.parseInt(m.group(1));
                    int g = Integer.parseInt(m.group(2));
                    int b = Integer.parseInt(m.group(3));
                    if (r != g || g != b) {
                        offenders.add(css + ":" + (i + 1) + "  " + m.group());
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "A tinted glow is the brand colour in another notation, and a rebrand would "
                        + "miss it. Use an alpha-variant token from theme.css. Found:\n  "
                        + String.join("\n  ", offenders));
    }

    @Test
    @DisplayName("every token a stylesheet refers to is actually defined")
    void referencedTokensAreDefined() throws IOException {
        Map<String, String> declared = declaredTokens();
        List<String> dangling = new ArrayList<>();

        for (Path css : viewStylesheets()) {
            String[] lines = read(css).split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                Matcher m = REFERENCE.matcher(lines[i]);
                while (m.find()) {
                    String token = m.group(1);
                    if (!declared.containsKey(token)) {
                        dangling.add(css + ":" + (i + 1) + "  " + token);
                    }
                }
            }
        }
        assertTrue(dangling.isEmpty(),
                "An undefined looked-up color silently renders as black rather than failing. Found:\n  "
                        + String.join("\n  ", dangling));
    }

    @Test
    @DisplayName("theme.css defines the tokens the palette is built on")
    void coreTokensExist() throws IOException {
        Map<String, String> declared = declaredTokens();
        for (String required : new String[]{"-brand-primary", "-brand-text", "-surface",
                                            "-success", "-danger", "-warning", "-focus-ring"}) {
            assertTrue(declared.containsKey(required), "theme.css is missing " + required);
        }
    }

    /**
     * TilesFX takes {@link Color} objects and never reads the scene's stylesheets, so a
     * handful of brand colours have to exist twice — once as a token and once as a Java
     * constant. Duplication that nothing checks is duplication that drifts, so this
     * checks it.
     */
    @Test
    @DisplayName("BrandPalette matches the tokens it mirrors")
    void javaPaletteMatchesTheStylesheet() throws IOException {
        Map<String, String> declared = declaredTokens();
        Map<String, Color> mirrors = new HashMap<>();
        mirrors.put("-chart-blue", BrandPalette.CHART_BLUE);
        mirrors.put("-success", BrandPalette.SUCCESS);
        mirrors.put("-danger", BrandPalette.DANGER);
        mirrors.put("-warning", BrandPalette.WARNING);

        for (Map.Entry<String, Color> entry : mirrors.entrySet()) {
            String token = entry.getKey();
            String declaredValue = declared.get(token);
            assertTrue(declaredValue != null, "theme.css no longer defines " + token);
            assertEquals(Color.web(declaredValue), entry.getValue(),
                    "BrandPalette has drifted from theme.css for " + token
                            + ": stylesheet says " + declaredValue);
        }
    }
}
