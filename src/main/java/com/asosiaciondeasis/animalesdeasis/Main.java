package com.asosiaciondeasis.animalesdeasis;

import javafx.application.Application;

/**
 * Entry point.
 *
 * <p>Deliberately does not extend {@link Application}. When the class holding
 * {@code main} is itself an {@code Application}, the JavaFX launcher checks that
 * the JavaFX modules were loaded from the module path and refuses to start
 * otherwise:</p>
 *
 * <pre>
 * Error: JavaFX runtime components are missing, and are required to run this
 * application
 * </pre>
 *
 * <p>An IDE runs a class by putting everything on the classpath, so pressing Run
 * failed while {@code mvn javafx:run} worked — the plugin builds a module path,
 * and the IDE does not.</p>
 *
 * <p>Starting from a class that is not an {@code Application} skips that check,
 * and JavaFX initialises from the classpath perfectly well. This is why the real
 * application lives in {@link AsisApplication}: so Run works from any IDE with no
 * launch configuration, and there is no second class anyone has to remember to
 * pick instead of this one.</p>
 */
public final class Main {

    private Main() {
        // Entry point only.
    }

    public static void main(String[] args) {
        Application.launch(AsisApplication.class, args);
    }
}
