package com.asosiaciondeasis.animalesdeasis.Model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rail is built from {@link NavigationSection}, so a mistake here is a menu
 * entry that opens nothing. These checks are cheap and catch the two ways that
 * happens: a path that does not resolve, and an id that changed.
 */
class NavigationSectionTest {

    @Test
    @DisplayName("every section points at an FXML file that exists")
    void everySectionResolvesItsView() {
        for (NavigationSection section : NavigationSection.values()) {
            assertNotNull(getClass().getResource(section.fxmlPath()),
                    section.label() + " points at " + section.fxmlPath()
                            + ", which is not on the classpath. The rail entry would open nothing.");
        }
    }

    @Test
    @DisplayName("ids are unique")
    void idsAreUnique() {
        Set<String> seen = new HashSet<>();
        for (NavigationSection section : NavigationSection.values()) {
            assertTrue(seen.add(section.id()),
                    "duplicate id " + section.id() + ": preferences would restore the wrong section");
        }
    }

    /**
     * Ids are written to the user's preferences, so they are effectively a stored
     * format. Renaming one silently invalidates what every existing installation
     * has remembered: people would be dropped back on the default section with no
     * explanation. Pinning them here makes that a deliberate decision rather than
     * a side effect of renaming a constant.
     */
    @Test
    @DisplayName("stored ids do not change")
    void storedIdsAreStable() {
        assertEquals("home", NavigationSection.HOME.id());
        assertEquals("animals", NavigationSection.ANIMALS.id());
        assertEquals("statistics", NavigationSection.STATISTICS.id());
    }

    @Test
    @DisplayName("an unknown id resolves to nothing rather than throwing")
    void unknownIdIsEmpty() {
        assertTrue(NavigationSection.byId("no-such-section").isEmpty());
        assertTrue(NavigationSection.byId(null).isEmpty());
    }

    @Test
    @DisplayName("every section carries an icon and a label")
    void everySectionIsPresentable() {
        for (NavigationSection section : NavigationSection.values()) {
            assertFalse(section.label().isBlank(), section + " has no label");
            assertFalse(section.iconLiteral().isBlank(),
                    section + " has no icon, so it would be invisible when the rail is collapsed");
        }
    }

    /**
     * When the rail collapses there is no room for group headings, so the grouping
     * moves into each item's tooltip and accessible name. If that string did not
     * name the group, collapsing would lose the structure entirely.
     */
    @Test
    @DisplayName("a grouped section names its group in the collapsed description")
    void groupedSectionsDescribeTheirGroup() {
        assertEquals("Gestión › Animales", NavigationSection.ANIMALS.accessibleDescription());
        assertEquals("Inicio", NavigationSection.HOME.accessibleDescription(),
                "Inicio stands outside any group, so its description is just its name");
        assertEquals("Análisis › Estadísticas", NavigationSection.STATISTICS.accessibleDescription());
    }
}
