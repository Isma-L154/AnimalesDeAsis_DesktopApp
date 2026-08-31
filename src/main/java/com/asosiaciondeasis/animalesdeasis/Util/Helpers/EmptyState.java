package com.asosiaciondeasis.animalesdeasis.Util.Helpers;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * What to show where a chart or a list would be, when there is nothing to put in
 * it.
 *
 * <p>The statistics dashboard had none. Selecting a year with no records drew
 * three sets of axes around empty space, and the pie chart went further: it
 * inserted a slice called "Sin datos" with a value of 1, producing a full green
 * circle. A reader sees a chart that says something — a complete, single-category
 * result — rather than a chart that has nothing to say.</p>
 *
 * <p>An empty state says which it is, and what would fill it.</p>
 */
public final class EmptyState {

    private EmptyState() {
    }

    /**
     * @param iconLiteral Ikonli literal suggesting the kind of content
     * @param title       what is absent, in a few words
     * @param detail      what would put something here
     */
    public static VBox create(String iconLiteral, String title, String detail) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.getStyleClass().add("empty-icon");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("empty-title");

        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().add("empty-detail");
        detailLabel.setWrapText(true);
        detailLabel.setMaxWidth(320);

        VBox box = new VBox(6, icon, titleLabel, detailLabel);
        box.getStyleClass().add("empty-state");
        box.setAlignment(Pos.CENTER);
        return box;
    }

    /**
     * Swaps a chart for an empty state, or back, in place.
     *
     * <p>Both live in the same {@link StackPane} and only one is ever visible.
     * Swapping visibility rather than adding and removing children keeps the
     * chart's own state — its series, its axis ranges — intact across a year with
     * no data, so returning to a year that has some does not rebuild it from
     * nothing.</p>
     *
     * @param hasData whether the chart has something to draw
     */
    public static void toggle(Node chart, Node emptyState, boolean hasData) {
        chart.setVisible(hasData);
        chart.setManaged(hasData);
        emptyState.setVisible(!hasData);
        emptyState.setManaged(!hasData);
    }

    /** Wraps a chart together with the empty state that stands in for it. */
    public static StackPane wrap(Node chart, Node emptyState) {
        StackPane stack = new StackPane(chart, emptyState);
        VBox.setVgrow(stack, Priority.ALWAYS);
        emptyState.setVisible(false);
        emptyState.setManaged(false);
        return stack;
    }
}
