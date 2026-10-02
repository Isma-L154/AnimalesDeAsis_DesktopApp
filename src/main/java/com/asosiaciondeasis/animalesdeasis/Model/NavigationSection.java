package com.asosiaciondeasis.animalesdeasis.Model;

import java.util.Arrays;
import java.util.Optional;

/**
 * The sections reachable from the navigation rail.
 *
 * <p>An enum rather than FXML paths typed at each call site: a typo in a path
 * compiles cleanly and fails at runtime with a null resource, and nothing ties a
 * loose path to the label or group it belongs to.</p>
 *
 * <p>{@link #id()} is what gets written to preferences. It is deliberately not
 * {@link #name()}: renaming a constant should not silently invalidate what every
 * existing installation has remembered.</p>
 */
public enum NavigationSection {

    HOME("home", "Inicio", null, "/fxml/HomeView.fxml", "fas-home"),
    ANIMALS("animals", "Animales", Group.MANAGEMENT, "/fxml/Animal/AnimalManagement.fxml", "fas-paw"),
    STATISTICS("statistics", "Estadísticas", Group.ANALYSIS, "/fxml/Statistics/StatisticsManagement.fxml", "fas-chart-bar");

    // Vacunas is deliberately absent: it needs a view that lists across animals,
    // and VaccineManagement is scoped to one, through setCurrentAnimal(). A rail
    // entry that opens nothing is worse than one that is not there.

    /** Headings in the rail. Collapsed, these become a divider and move into the tooltip. */
    public enum Group {
        MANAGEMENT("Gestión"),
        ANALYSIS("Análisis");

        private final String label;

        Group(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final String id;
    private final String label;
    private final Group group;
    private final String fxmlPath;
    private final String iconLiteral;

    NavigationSection(String id, String label, Group group, String fxmlPath, String iconLiteral) {
        this.id = id;
        this.label = label;
        this.group = group;
        this.fxmlPath = fxmlPath;
        this.iconLiteral = iconLiteral;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    /** {@code null} for sections that stand outside any group, such as Inicio. */
    public Group group() {
        return group;
    }

    public String fxmlPath() {
        return fxmlPath;
    }

    /** Ikonli FontAwesome 5 literal, e.g. {@code fas-paw}. */
    public String iconLiteral() {
        return iconLiteral;
    }

    /**
     * What a collapsed rail shows on hover or focus: the full path to the
     * section, so grouping survives losing the headings.
     */
    public String accessibleDescription() {
        return group == null ? label : group.label() + " › " + label;
    }

    public static Optional<NavigationSection> byId(String id) {
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }
}
