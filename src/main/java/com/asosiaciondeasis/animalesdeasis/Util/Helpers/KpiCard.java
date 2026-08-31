package com.asosiaciondeasis.animalesdeasis.Util.Helpers;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * A single headline figure: caption, value, and an optional note beneath.
 *
 * <p>Shared so the home panel and the statistics dashboard show numbers the same
 * way. The dashboard previously used TilesFX, which brought its own typography,
 * its own palette and its own fixed 250x150 geometry — three saturated colours
 * that belonged to no part of this application, sitting in a row that left a gap
 * wherever the window was wider than 810 pixels.</p>
 *
 * <p>Styled entirely through {@code theme.css}, so a rebrand reaches it like
 * everything else. That was the specific thing TilesFX could not do: its colours
 * were {@code Color} objects built in Java, invisible to the stylesheet.</p>
 */
public final class KpiCard {

    private KpiCard() {
    }

    /**
     * @param iconLiteral Ikonli literal, e.g. {@code fas-paw}
     * @param caption     what the figure is
     * @param value       the figure itself, already formatted
     * @param note        a smaller line beneath, or {@code null}
     * @param emphasise   draws attention: for a number someone should act on,
     *                    not for one that is merely large
     */
    public static VBox create(String iconLiteral, String caption, String value,
                              String note, boolean emphasise) {
        FontIcon glyph = new FontIcon(iconLiteral);
        glyph.getStyleClass().add("kpi-icon");

        Label captionLabel = new Label(caption, glyph);
        captionLabel.getStyleClass().add("kpi-caption");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("kpi-value");

        VBox card = new VBox(2, captionLabel, valueLabel);
        card.getStyleClass().addAll("kpi-card", "surface-card");
        if (emphasise) {
            card.getStyleClass().add("kpi-warn");
        }
        if (note != null && !note.isBlank()) {
            Label noteLabel = new Label(note);
            noteLabel.getStyleClass().add("kpi-note");
            card.getChildren().add(noteLabel);
        }

        // Cards share the row evenly rather than each taking a fixed width, so
        // there is no dead space on a wide window and no clipping on a narrow one.
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }
}
