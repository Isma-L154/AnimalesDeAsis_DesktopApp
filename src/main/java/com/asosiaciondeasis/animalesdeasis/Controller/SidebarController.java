package com.asosiaciondeasis.animalesdeasis.Controller;

import com.asosiaciondeasis.animalesdeasis.Model.NavigationSection;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.EnumMap;
import java.util.Map;

/**
 * The navigation rail.
 *
 * <p>Built in code rather than declared in FXML because the rail has two forms —
 * expanded with labels and group headings, collapsed to icons — and keeping one
 * set of nodes that switches between them is far less error-prone than keeping
 * two markup trees in step.</p>
 *
 * <p><b>Items are buttons, not labels.</b> The previous rail used {@code Label}
 * with an {@code onMouseClicked} handler. A {@code Label} is not focus
 * traversable, so the entire application was unreachable without a mouse: no
 * tab stop, no Enter, nothing. A {@code Button} is focusable and handles Enter
 * and Space natively, which is most of keyboard support arriving for free.</p>
 */
public class SidebarController {

    private static final String ACTIVE_CLASS = "active";
    private static final double EXPANDED_WIDTH = 180;
    private static final double COLLAPSED_WIDTH = 72;

    private final Map<NavigationSection, Button> items = new EnumMap<>(NavigationSection.class);
    private final Map<NavigationSection.Group, Node> groupHeadings =
            new EnumMap<>(NavigationSection.Group.class);

    private VBox root;
    private boolean collapsed;

    /**
     * Builds the rail and returns its root node.
     *
     * @param onSelect invoked with the chosen section when an item is activated
     */
    public VBox build(java.util.function.Consumer<NavigationSection> onSelect) {
        root = new VBox();
        root.getStyleClass().add("sidebar");
        root.setFillWidth(true);

        NavigationSection.Group currentGroup = null;
        for (NavigationSection section : NavigationSection.values()) {
            NavigationSection.Group group = section.group();
            if (group != null && group != currentGroup) {
                root.getChildren().add(createGroupHeading(group));
                currentGroup = group;
            }
            root.getChildren().add(createItem(section, onSelect));
        }

        applyCollapsed(collapsed);
        return root;
    }

    /**
     * A group heading has two appearances. Expanded it is the group's name in
     * small caps; collapsed there is no room for text, so the same node becomes a
     * divider rule holding the same vertical position. The grouping is not lost,
     * it moves into the tooltip — see {@link NavigationSection#accessibleDescription()}.
     */
    private Node createGroupHeading(NavigationSection.Group group) {
        Label heading = new Label(group.label().toUpperCase());
        heading.getStyleClass().add("sidebar-group");

        Region divider = new Region();
        divider.getStyleClass().add("sidebar-divider");

        VBox holder = new VBox(heading, divider);
        holder.getStyleClass().add("sidebar-group-holder");
        // Headings are decorative once the tooltip carries the same information,
        // and a screen reader should not stop on them between items.
        heading.setFocusTraversable(false);
        groupHeadings.put(group, holder);
        return holder;
    }

    private Button createItem(NavigationSection section,
                              java.util.function.Consumer<NavigationSection> onSelect) {
        FontIcon icon = new FontIcon(section.iconLiteral());
        icon.getStyleClass().add("sidebar-icon");

        Button item = new Button();
        item.setGraphic(icon);
        item.setText(section.label());
        item.setContentDisplay(ContentDisplay.LEFT);
        item.setGraphicTextGap(10);
        item.setMaxWidth(Double.MAX_VALUE);
        item.setAlignment(Pos.CENTER_LEFT);
        item.getStyleClass().add("sidebar-item");
        item.setFocusTraversable(true);
        item.setOnAction(e -> onSelect.accept(section));

        // What a screen reader announces, and what the tooltip shows when the rail
        // is collapsed and the label is gone.
        item.setAccessibleText(section.accessibleDescription());
        Tooltip tooltip = new Tooltip(section.accessibleDescription());
        tooltip.setShowDelay(Duration.millis(350));
        item.setTooltip(tooltip);

        items.put(section, item);
        return item;
    }

    /** Highlights {@code selected} and clears the flag from the others. */
    public void markActive(NavigationSection selected) {
        items.forEach((section, button) -> {
            button.getStyleClass().remove(ACTIVE_CLASS);
            if (section == selected) {
                button.getStyleClass().add(ACTIVE_CLASS);
            }
        });
    }

    public void setCollapsed(boolean value) {
        this.collapsed = value;
        if (root != null) {
            applyCollapsed(value);
        }
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    private void applyCollapsed(boolean value) {
        double width = value ? COLLAPSED_WIDTH : EXPANDED_WIDTH;
        root.setPrefWidth(width);
        root.setMinWidth(width);
        root.setMaxWidth(width);
        root.pseudoClassStateChanged(
                javafx.css.PseudoClass.getPseudoClass("collapsed"), value);

        for (Button item : items.values()) {
            // The label goes; the icon and the tooltip stay. Text is hidden with
            // ContentDisplay rather than by removing the string, so the accessible
            // name and the tooltip still carry the section's full path.
            item.setContentDisplay(value ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT);
            item.setAlignment(value ? Pos.CENTER : Pos.CENTER_LEFT);
        }
        for (Node heading : groupHeadings.values()) {
            VBox holder = (VBox) heading;
            Node title = holder.getChildren().get(0);
            Node divider = holder.getChildren().get(1);
            title.setVisible(!value);
            title.setManaged(!value);
            divider.setVisible(value);
            divider.setManaged(value);
        }
    }
}
