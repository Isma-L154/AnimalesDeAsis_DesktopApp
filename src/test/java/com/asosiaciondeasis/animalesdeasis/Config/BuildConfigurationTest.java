package com.asosiaciondeasis.animalesdeasis.Config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the one build property that can break the application everywhere while
 * leaving CI green.
 *
 * <p>Dependabot once moved JavaFX from 17.0.10 straight to 26.0.2 and every
 * check passed. The bump was not safe — JavaFX 26 ships class files a Java 17
 * runtime cannot load — but CI installs a <em>Full</em> JDK, whose bundled
 * JavaFX shadows the Maven artifact at runtime. So the pipeline exercised
 * JavaFX 17 while the project declared 26, and the mismatch only surfaced on a
 * plain JDK, where the application would not start at all:</p>
 *
 * <pre>
 * java.lang.UnsupportedClassVersionError: javafx/scene/paint/Color has been
 * compiled by a more recent version of the Java Runtime (class file version
 * 68.0), this version of the Java Runtime only recognizes class file versions
 * up to 65.0
 * </pre>
 *
 * <p>Green CI on a build that cannot start anywhere else is worse than a red
 * one. This reads the declared versions out of the POM and fails when they stop
 * agreeing, so the next attempt is caught by the pipeline rather than by
 * someone's installer.</p>
 */
class BuildConfigurationTest {

    private static final Path POM = Paths.get("pom.xml");

    private static String pom() throws IOException {
        return new String(Files.readAllBytes(POM), StandardCharsets.UTF_8);
    }

    private static String property(String name) throws IOException {
        Matcher m = Pattern.compile("<" + name + ">([^<]+)</" + name + ">").matcher(pom());
        assertTrue(m.find(), "pom.xml declares no <" + name + ">");
        return m.group(1).trim();
    }

    @Test
    @DisplayName("JavaFX major version matches the Java release the project targets")
    void javafxMajorMatchesJavaTarget() throws IOException {
        String javaTarget = property("maven.compiler.target");
        String javafx = property("javafx.version");
        String javafxMajor = javafx.split("\\.")[0];

        assertEquals(javaTarget, javafxMajor,
                "pom.xml targets Java " + javaTarget + " but declares JavaFX " + javafx + ".\n"
                        + "JavaFX tracks the JDK it is built for, so a mismatch ships class files the "
                        + "target runtime cannot load. CI will not catch this on its own: it installs a "
                        + "Full JDK whose bundled JavaFX shadows the Maven artifact, so the pipeline goes "
                        + "green while the application fails to start everywhere else.\n"
                        + "Moving JavaFX to a new major line means moving the JDK with it, in the POM and "
                        + "in every workflow.");
    }

    /**
     * maven-compiler-plugin's {@code <release>} overrides
     * {@code maven.compiler.source} and {@code target} without saying so. This
     * POM carried a literal 17 there while both properties said 21, so the build
     * quietly kept compiling for the old release - and the check below, which
     * only compared the two properties, saw nothing wrong. It reads the property
     * now, and this makes sure it keeps doing so.
     */
    @Test
    @DisplayName("the compiler release is not pinned behind the properties")
    void releaseFollowsTheDeclaredTarget() throws IOException {
        Matcher m = Pattern.compile("<release>([^<]+)</release>").matcher(pom());
        while (m.find()) {
            String value = m.group(1).trim();
            assertTrue(value.startsWith("${"),
                    "<release> is set to the literal " + value + ", which silently overrides "
                            + "maven.compiler.source and target. Derive it from the property "
                            + "instead, so there is one number to change.");
        }
    }

    @Test
    @DisplayName("compiler source and target agree")
    void compilerSourceAndTargetAgree() throws IOException {
        assertEquals(property("maven.compiler.source"), property("maven.compiler.target"),
                "compiling against one Java release and targeting another produces class files that "
                        + "load but fail on the first API the older runtime lacks");
    }
}
