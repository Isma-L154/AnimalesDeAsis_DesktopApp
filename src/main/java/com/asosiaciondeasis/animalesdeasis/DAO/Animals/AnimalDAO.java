package com.asosiaciondeasis.animalesdeasis.DAO.Animals;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Animals.DuplicateChipException;
import com.asosiaciondeasis.animalesdeasis.Abstraccions.Animals.IAnimalDAO;
import com.asosiaciondeasis.animalesdeasis.Abstraccions.RowVersion;
import com.asosiaciondeasis.animalesdeasis.DAO.RowVersionGuard;
import com.asosiaciondeasis.animalesdeasis.DAO.Transactions;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQLite implementation of {@link IAnimalDAO}. Each call opens its own
 * connection.
 *
 * <p>{@code admission_date} is stored as ISO 8601 ({@code 2025-09-02T00:00:00}),
 * which is why {@code ORDER BY} on the raw column sorts chronologically and why
 * {@code strftime} can read a year out of it.</p>
 */
public class AnimalDAO implements IAnimalDAO {

    /** Every column, in the order {@link #bind} fills them. */
    private static final String COLUMNS = """
            record_number, chip_number, barcode, admission_date,
            collected_by, place_id, reason_for_rescue, species,
            approximate_age, sex, name, ailments, neutering_date, adopted,
            synced, active, last_modified""";

    /** A missing timestamp is stamped now rather than breaking {@code NOT NULL}. */
    private static final String VALUES =
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, datetime('now', 'utc')))";

    private final DataSource dataSource;

    public AnimalDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insertAnimal(Animal animal) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("INSERT INTO animals (" + COLUMNS + ") " + VALUES)) {
            bind(pstmt, animal);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw translate(e, "Error inserting animal " + animal.getRecordNumber());
        }
    }

    @Override
    public List<Animal> getAllAnimals() throws Exception {
        return queryAnimals("SELECT * FROM animals WHERE active = 1 ORDER BY admission_date DESC");
    }

    @Override
    public Animal findByRecordNumber(String recordNumber) throws Exception {
        List<Animal> found = queryAnimals("SELECT * FROM animals WHERE record_number = ?", recordNumber);
        return found.isEmpty() ? null : found.get(0);
    }

    /**
     * Dates arrive as {@code yyyy-MM-dd}, which compares correctly against the
     * stored ISO timestamps.
     */
    @Override
    public List<Animal> findByFilters(String species, String startDate, String endDate, String chipNumber, Boolean showInactive) throws Exception {
        StringBuilder sql = new StringBuilder("SELECT * FROM animals WHERE 1=1");
        List<Object> params = new ArrayList<>();

        sql.append(Boolean.TRUE.equals(showInactive) ? " AND active = 0" : " AND active = 1");
        if (species != null && !species.isBlank()) {
            sql.append(" AND species = ?");
            params.add(species);
        }
        if (startDate != null && endDate != null) {
            sql.append(" AND admission_date BETWEEN ? AND ?");
            params.add(startDate);
            params.add(endDate);
        }
        if (chipNumber != null && !chipNumber.isBlank()) {
            sql.append(" AND chip_number LIKE ?");
            params.add("%" + chipNumber + "%");
        }
        sql.append(" ORDER BY admission_date DESC");

        return queryAnimals(sql.toString(), params.toArray());
    }

    @Override
    public void updateAnimal(Animal animal) throws Exception {
        String sql = """
                UPDATE animals
                SET chip_number = ?, barcode = ?, admission_date = ?, collected_by = ?, place_id = ?,
                    reason_for_rescue = ?, species = ?, approximate_age = ?, sex = ?, name = ?,
                    ailments = ?, neutering_date = ?, adopted = ?, active = ?, synced = ?,
                    last_modified = datetime('now', 'utc')
                WHERE record_number = ?
                """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
            pstmt.setString(16, animal.getRecordNumber());

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
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, active ? 1 : 0);
            pstmt.setString(2, recordNumber);
            if (pstmt.executeUpdate() == 0) {
                throw new Exception("No animal found with record number " + recordNumber);
            }
        }
    }

    // -------------------------------------------------------------------------
    //  Synchronisation
    // -------------------------------------------------------------------------

    @Override
    public List<Animal> getUnsyncedAnimals() throws Exception {
        return queryAnimals("SELECT * FROM animals WHERE synced = 0");
    }

    @Override
    public Map<String, RowVersion> getRowVersions() throws Exception {
        Map<String, RowVersion> result = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT record_number, last_modified, synced FROM animals");
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getString(1), new RowVersion(rs.getString(2), rs.getInt(3) == 1));
            }
        }
        return result;
    }

    @Override
    public void saveFromRemote(List<Animal> animals, Map<String, RowVersion> expected) throws Exception {
        if (animals.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO animals (" + COLUMNS + ") " + VALUES + """

                ON CONFLICT(record_number) DO UPDATE SET
                    chip_number = excluded.chip_number, barcode = excluded.barcode,
                    admission_date = excluded.admission_date, collected_by = excluded.collected_by,
                    place_id = excluded.place_id, reason_for_rescue = excluded.reason_for_rescue,
                    species = excluded.species, approximate_age = excluded.approximate_age,
                    sex = excluded.sex, name = excluded.name, ailments = excluded.ailments,
                    neutering_date = excluded.neutering_date, adopted = excluded.adopted,
                    synced = excluded.synced, active = excluded.active,
                    last_modified = excluded.last_modified
                WHERE animals.last_modified IS ? AND animals.synced IS ?
                """;
        Transactions.inTransaction(dataSource, conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (Animal animal : animals) {
                    bind(pstmt, animal);
                    RowVersionGuard.bind(pstmt, 18, expected.get(animal.getRecordNumber()));
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }
            return null;
        });
    }

    @Override
    public int markSynced(List<Animal> pushed) throws Exception {
        if (pushed.isEmpty()) {
            return 0;
        }
        // IS rather than = so that NULL matches NULL.
        String sql = """
                UPDATE animals SET synced = 1
                WHERE record_number = ? AND chip_number IS ? AND barcode IS ? AND admission_date IS ?
                  AND collected_by IS ? AND place_id IS ? AND reason_for_rescue IS ? AND species IS ?
                  AND approximate_age IS ? AND sex IS ? AND name IS ? AND ailments IS ?
                  AND neutering_date IS ? AND adopted IS ? AND active IS ? AND last_modified IS ?
                """;
        return Transactions.inTransaction(dataSource, conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (Animal animal : pushed) {
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
                    pstmt.setInt(15, animal.isActive() ? 1 : 0);
                    pstmt.setString(16, animal.getLastModified());
                    pstmt.addBatch();
                }
                int marked = 0;
                for (int count : pstmt.executeBatch()) {
                    marked += Math.max(count, 0);
                }
                return marked;
            }
        });
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
        return count("SELECT COUNT(*) FROM animals WHERE adopted = 1 AND strftime('%Y', admission_date) = ?",
                String.valueOf(year));
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

    private int count(String sql, Object... params) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            setParams(pstmt, params);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private List<Animal> queryAnimals(String sql, Object... params) throws SQLException {
        List<Animal> animals = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            setParams(pstmt, params);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    animals.add(mapResultSetToAnimal(rs));
                }
            }
        }
        return animals;
    }

    private static void setParams(PreparedStatement pstmt, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            pstmt.setObject(i + 1, params[i]);
        }
    }

    /** Fills parameters 1-17 in {@link #COLUMNS} order. */
    private static void bind(PreparedStatement pstmt, Animal animal) throws SQLException {
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
