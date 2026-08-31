package com.asosiaciondeasis.animalesdeasis.Model;

import java.util.List;

/**
 * Everything the home panel shows, gathered in one go.
 *
 * <p>A record rather than a bag of fields because it is read-only by nature: it
 * is assembled on a background thread and handed to the interface thread, and
 * nothing should be able to change it in between.</p>
 *
 * @param inShelter          animals present, meaning active and not adopted
 * @param adoptedThisYear    animals adopted whose admission falls in {@code year}
 * @param year               the year the adoption figures cover
 * @param recentAdmissions   the most recently admitted animals, newest first
 * @param missingVaccines    active animals with no vaccination on record
 * @param missingChip        active animals with no chip number
 * @param pendingUpload      records not yet pushed to Firebase
 */
public record ShelterSummary(
        int inShelter,
        int adoptedThisYear,
        int year,
        List<Animal> recentAdmissions,
        List<Animal> missingVaccines,
        List<Animal> missingChip,
        int pendingUpload) {

    /** Defensive copies: a record's fields are final, the lists behind them are not. */
    public ShelterSummary {
        recentAdmissions = List.copyOf(recentAdmissions);
        missingVaccines = List.copyOf(missingVaccines);
        missingChip = List.copyOf(missingChip);
    }

    /**
     * Adoptions as a share of the animals handled this year.
     *
     * <p>Returns 0 when there is nothing to divide by, rather than NaN. A panel
     * showing "NaN%" on the day the association starts using the application
     * would be its first impression of the tool.</p>
     */
    public double adoptionRate() {
        int handled = inShelter + adoptedThisYear;
        return handled == 0 ? 0 : (adoptedThisYear * 100.0) / handled;
    }

    /** Whether anything at all needs attention, so the panel can say so plainly. */
    public boolean hasAttentionItems() {
        return !missingVaccines.isEmpty() || !missingChip.isEmpty() || pendingUpload > 0;
    }
}
