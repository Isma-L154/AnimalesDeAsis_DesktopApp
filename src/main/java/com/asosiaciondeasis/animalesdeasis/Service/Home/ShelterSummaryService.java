package com.asosiaciondeasis.animalesdeasis.Service.Home;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Animals.IAnimalDAO;
import com.asosiaciondeasis.animalesdeasis.Model.ShelterSummary;

import java.time.LocalDate;

/**
 * Assembles the figures behind the home panel.
 *
 * <p>Exists so the controller asks one question instead of six. That keeps the
 * background task simple — one call, one result, one hop back to the interface
 * thread — and puts the definition of "in the shelter" or "needs attention" in a
 * place that can be tested without a scene graph.</p>
 *
 * <p><b>This is not a transaction.</b> The queries run one after another against
 * a live database, so a record could change between the first and the last and
 * the panel would show figures that never existed together at any instant. That
 * is acceptable here and deliberate: this is a glance at a shelter's day, not an
 * audited statement, and the cost of holding a transaction open across six
 * queries at startup is worse than a count being a second stale.</p>
 */
public class ShelterSummaryService {

    /** Enough rows to be useful in a panel without turning it into a list screen. */
    private static final int RECENT_LIMIT = 5;
    private static final int ATTENTION_LIMIT = 5;

    private final IAnimalDAO animalDAO;

    public ShelterSummaryService(IAnimalDAO animalDAO) {
        this.animalDAO = animalDAO;
    }

    /**
     * Runs every query the panel needs.
     *
     * <p>Blocking and slow by design — call it from a background thread, never
     * from the JavaFX application thread.</p>
     */
    public ShelterSummary load() throws Exception {
        int year = LocalDate.now().getYear();
        return new ShelterSummary(
                animalDAO.countInShelter(),
                animalDAO.countAdoptedInYear(year),
                year,
                animalDAO.getRecentAdmissions(RECENT_LIMIT),
                animalDAO.findWithoutVaccines(ATTENTION_LIMIT),
                animalDAO.findWithoutChip(ATTENTION_LIMIT),
                animalDAO.countUnsynced());
    }
}
