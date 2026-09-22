package com.asosiaciondeasis.animalesdeasis.DAO.Animals;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Animals.DuplicateChipException;
import com.asosiaciondeasis.animalesdeasis.Abstraccions.Animals.IAnimalDAO;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite implementation of {@link IAnimalDAO}.
 *
 * <p>{@code admission_date} is stored as ISO 8601 ({@code 2025-09-02T00:00:00}),
 * which is why {@code ORDER BY} on the raw column sorts chronologically and why
 * {@code strftime} can read a year out of it.</p>
 */
public class AnimalDAO implements IAnimalDAO {

    private final Connection conn;

    public AnimalDAO(Connection conn) {
        this.conn = conn;
    }

    /**
     * Inserts a new animal. The record number is assigned by the model before this
     * is called; {@code last_modified} is kept when the record comes from Firebase
     * and stamped now otherwise.
     */
    @Override
    public void insertAnimal(Animal animal) throws Exception {
        String sql = """
                INSERT INTO animals (
                    record_number, chip_number, barcode, admission_date,
                    collected_by, place_id, reason_for_rescue, species,
                    approximate_age, sex, name, ailments, neutering_date, adopted,
                    synced, active, last_modified
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, datetime('now', 'utc')))
                """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, animal.getRecordNumber());
            pstmt.setString(2, animal.getChipNumber());
            pstmt.setString(3, animal.getBarcode());
            pstmt.setString(4, animal.getAdmissionDate());
            pstmt.setString(5, animal.getCollectedBy());
            pstmt.setInt(6, animal.getPlaceId());
            pstmt.setString(7, animal.getReasonForRescue());
            pstmt.setString(8, animal.getSpecies());
            pstmt.setInt(9, animal.getApproximateAge());
            pstmt.setString(10, animal.getSex());
            pstmt.setString(11, animal.getName());
            pstmt.setString(12, animal.getAilments());
            pstmt.setString(13, animal.getNeuteringDate());
            pstmt.setInt(14, animal.isAdopted() ? 1 : 0);
            pstmt.setInt(15, animal.isSynced() ? 1 : 0);
            pstmt.setInt(16, animal.isActive() ? 1 : 0);
            pstmt.setString(17, blankToNull(animal.getLastModified()));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw translate(e, "Error inserting animal " + animal.getRecordNumber());
        }
    }

    @Override
    public List<Animal> getAllAnimals() throws Exception {
        return queryAnimals("SELECT * FROM animals WHERE active = 1 ORDER BY admission_date DESC");
    }

    /** @return the animal, or {@code null} when no record has that number */
    @Override
    public Animal findByRecordNumber(String recordNumber) throws Exception {
        String sql = "SELECT * FROM animals WHERE record_number = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, recordNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapResultSetToAnimal(rs) : null;
            }
        }
    }

    /**
     * Dates arrive as {@code yyyy-MM-dd}, which compares correctly against the
     * stored ISO timestamps.
     */
    @Override
    public List<Animal> findByFilters(String species, String startDate, String endDate, String chipNumber, Boolean showInactive) throws Exception {
        List<Animal> animals = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM animals WHERE 1=1");

        sql.append(Boolean.TRUE.equals(showInactive) ? " AND active = 0" : " AND active = 1");

        boolean bySpecies = species != null && !species.isBlank();
        boolean byDates = startDate != null && endDate != null;
        boolean byChip = chipNumber != null && !chipNumber.isBlank();

        if (bySpecies) {
            sql.append(" AND species = ?");
        }
        if (byDates) {
            sql.append(" AND admission_date BETWEEN ? AND ?");
        }
        if (byChip) {
            sql.append(" AND chip_number LIKE ?");
        }
        sql.append(" ORDER BY admission_date DESC");

        try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            int index = 1;
            if (bySpecies) {
                pstmt.setString(index++, species);
            }
            if (byDates) {
                pstmt.setString(index++, startDate);
                pstmt.setString(index++, endDate);
            }
            if (byChip) {
                pstmt.setString(index, "%" + chipNumber + "%");
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    animals.add(mapResultSetToAnimal(rs));
                }
            }
        } catch (SQLException e) {
            throw new Exception("Error fetching animals by filters", e);
        }
        return animals;
    }

    /**
     * @param timestamp {@code true} for a local edit, which stamps
     *                  {@code last_modified} now so the next sync pushes it;
     *                  {@code false} when applying a record pulled from Firebase,
     *                  which keeps the remote timestamp so the two stay comparable
     */
    @Override
    public void updateAnimal(Animal animal, boolean timestamp) throws Exception {
        String sql = """
                UPDATE animals
                SET chip_number = ?, barcode = ?, admission_date = ?, collected_by = ?, place_id = ?,
                    reason_for_rescue = ?, species = ?, approximate_age = ?, sex = ?, name = ?,
                    ailments = ?, neutering_date = ?, adopted = ?, active = ?, synced = ?,
                    last_modified = COALESCE(?, datetime('now', 'utc'))
                WHERE record_number = ?
                """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, animal.getChipNumber());
            pstmt.setString(2, animal.getBarcode());
            pstmt.setString(3, animal.getAdmissionDate());
            pstmt.setString(4, animal.getCollectedBy());
            pstmt.setInt(5, animal.getPlaceId());
            pstmt.setString(6, animal.getReasonForRescue());
            pstmt.setString(7, animal.getSpecies());
            pstmt.setInt(8, animal.getApproximateAge());
            pstmt.setString(9, animal.getSex());
            pstmt.setString(10, animal.getName());
            pstmt.setString(11, animal.getAilments());
            pstmt.setString(12, animal.getNeuteringDate());
            pstmt.setInt(13, animal.isAdopted() ? 1 : 0);
            pstmt.setInt(14, animal.isActive() ? 1 : 0);
            pstmt.setInt(15, animal.isSynced() ? 1 : 0);
            pstmt.setString(16, timestamp ? null : blankToNull(animal.getLastModified()));
            pstmt.setString(17, animal.getRecordNumber());

            if (pstmt.executeUpdate() == 0) {
                throw new Exception("No animal found with record number " + animal.getRecordNumber());
            }
        } catch (SQLException e) {
            throw translate(e, "Error updating animal " + animal.getRecordNumber());
        }
    }

    /** Logical delete: the row stays, flagged inactive, so the deletion synchronises. */
    @Override
    public void deleteAnimal(String recordNumber) throws Exception {
        setActive(recordNumber, false);
    }

    @Override
    public void reactivateAnimal(String recordNumber) throws Exception {
        setActive(recordNumber, true);
    }

    private void setActive(String recordNumber, boolean active) throws Exception {
        String sql = "UPDATE animals SET active = ?, synced = 0, last_modified = datetime('now', 'utc') "
                + "WHERE record_number = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, active ? 1 : 0);
            pstmt.setString(2, recordNumber);
            if (pstmt.executeUpdate() == 0) {
                throw new Exception("No animal found with record number " + recordNumber);
            }
        }
    }

    @Override
    public List<Animal> getUnsyncedAnimals() throws Exception {
        return queryAnimals("SELECT * FROM animals WHERE synced = 0");
    }

    // -------------------------------------------------------------------------
    //  Home panel
    // -------------------------------------------------------------------------
    //  Counting and capping happen in SQL. The panel issues all of these when the
    //  application opens, so returning a number rather than a list of rows to
    //  measure in Java is what keeps that from being felt.

    @Override
    public int countInShelter() throws Exception {
        return count("SELECT COUNT(*) FROM animals WHERE active = 1 AND adopted = 0");
    }

    @Override
    public int countAdoptedInYear(int year) throws Exception {
        String sql = "SELECT COUNT(*) FROM animals "
                   + "WHERE adopted = 1 AND strftime('%Y', admission_date) = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, String.valueOf(year));
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    @Override
    public List<Animal> getRecentAdmissions(int limit) throws Exception {
        return queryAnimals("SELECT * FROM animals WHERE active = 1 "
                + "ORDER BY admission_date DESC LIMIT ?", limit);
    }

    /**
     * NOT EXISTS rather than a LEFT JOIN with a null test: it stops at the first
     * matching vaccine instead of building the join for every one an animal has.
     */
    @Override
    public List<Animal> findWithoutVaccines(int limit) throws Exception {
        return queryAnimals("""
                SELECT * FROM animals a
                WHERE a.active = 1
                  AND NOT EXISTS (
                      SELECT 1 FROM vaccines v
                      WHERE v.animal_record_number = a.record_number
                  )
                ORDER BY a.admission_date DESC
                LIMIT ?
                """, limit);
    }

    @Override
    public List<Animal> findWithoutChip(int limit) throws Exception {
        // A missing chip reaches the database as NULL from the form and as an
        // empty string from an import, so both count as missing.
        return queryAnimals("""
                SELECT * FROM animals
                WHERE active = 1
                  AND (chip_number IS NULL OR TRIM(chip_number) = '')
                ORDER BY admission_date DESC
                LIMIT ?
                """, limit);
    }

    @Override
    public int countUnsynced() throws Exception {
        return count("SELECT COUNT(*) FROM animals WHERE synced = 0 AND active = 1");
    }

    private int count(String sql) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private List<Animal> queryAnimals(String sql, Object... params) throws SQLException {
        List<Animal> animals = new ArrayList<>();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    animals.add(mapResultSetToAnimal(rs));
                }
            }
        }
        return animals;
    }

    /** Surfaces a chip collision as its own type; everything else keeps its cause. */
    private static Exception translate(SQLException e, String context) {
        String message = e.getMessage();
        if (message != null && message.contains("UNIQUE constraint failed")
                && (message.contains("chip_number") || message.contains("barcode"))) {
            return new DuplicateChipException(e);
        }
        return new Exception(context, e);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private Animal mapResultSetToAnimal(ResultSet rs) throws SQLException {
        Animal animal = Animal.fromExistingRecord(rs.getString("record_number"));
        animal.setChipNumber(rs.getString("chip_number"));
        animal.setBarcode(rs.getString("barcode"));
        animal.setAdmissionDate(rs.getString("admission_date"));
        animal.setCollectedBy(rs.getString("collected_by"));
        animal.setPlaceId(rs.getInt("place_id"));
        animal.setReasonForRescue(rs.getString("reason_for_rescue"));
        animal.setSpecies(rs.getString("species"));
        animal.setApproximateAge(rs.getInt("approximate_age"));
        animal.setSex(rs.getString("sex"));
        animal.setName(rs.getString("name"));
        animal.setAilments(rs.getString("ailments"));
        animal.setNeuteringDate(rs.getString("neutering_date"));
        animal.setAdopted(rs.getInt("adopted") == 1);
        animal.setSynced(rs.getInt("synced") == 1);
        animal.setActive(rs.getInt("active") == 1);
        animal.setLastModified(rs.getString("last_modified"));
        return animal;
    }
}
