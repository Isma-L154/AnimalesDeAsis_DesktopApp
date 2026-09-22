package com.asosiaciondeasis.animalesdeasis.DAO.Vaccine;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Vaccines.IVaccineDAO;
import com.asosiaciondeasis.animalesdeasis.Abstraccions.RowVersion;
import com.asosiaciondeasis.animalesdeasis.DAO.RowVersionGuard;
import com.asosiaciondeasis.animalesdeasis.DAO.Transactions;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** SQLite implementation of {@link IVaccineDAO}. Each call opens its own connection. */
public class VaccineDAO implements IVaccineDAO {

    private static final String COLUMNS = "id, animal_record_number, vaccine_name, vaccination_date, synced, last_modified";
    /** A missing timestamp is stamped now rather than breaking {@code NOT NULL}. */
    private static final String VALUES = "VALUES (?, ?, ?, ?, ?, COALESCE(?, datetime('now', 'utc')))";

    private final DataSource dataSource;

    public VaccineDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insertVaccine(Vaccine vaccine) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("INSERT INTO vaccines (" + COLUMNS + ") " + VALUES)) {
            bind(pstmt, vaccine);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new Exception("Error inserting vaccine", e);
        }
    }

    @Override
    public List<Vaccine> getVaccinesByAnimal(String animalRecordNumber) throws Exception {
        return queryVaccines("SELECT * FROM vaccines WHERE animal_record_number = ? ORDER BY vaccination_date DESC",
                animalRecordNumber);
    }

    @Override
    public Vaccine findById(String id) throws Exception {
        List<Vaccine> found = queryVaccines("SELECT * FROM vaccines WHERE id = ?", id);
        return found.isEmpty() ? null : found.get(0);
    }

    @Override
    public void updateVaccine(Vaccine vaccine) throws Exception {
        String sql = """
                UPDATE vaccines
                SET vaccine_name = ?, vaccination_date = ?, synced = ?, last_modified = datetime('now', 'utc')
                WHERE id = ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, vaccine.getVaccineName());
            pstmt.setString(2, vaccine.getVaccinationDate());
            pstmt.setInt(3, vaccine.isSynced() ? 1 : 0);
            pstmt.setString(4, vaccine.getId());
            if (pstmt.executeUpdate() == 0) {
                throw new Exception("No vaccine found with ID: " + vaccine.getId());
            }
        } catch (SQLException e) {
            throw new Exception("Error updating vaccine", e);
        }
    }

    /**
     * Deletes a vaccine and records that it was deleted.
     *
     * <p>The row itself is removed - this is a hard delete, unlike animals, which
     * carry an {@code active} flag that synchronises like any other change. The
     * tombstone in {@code deleted_vaccines} is what makes the deletion survive
     * long enough to reach Firebase; without it the next pull found the row still
     * in Firebase, saw nothing locally, and put it back.</p>
     */
    @Override
    public void deleteVaccine(String id) throws Exception {
        Transactions.inTransaction(dataSource, conn -> {
            // Read the owning animal before the row goes: the tombstone needs it to
            // address the remote document, and afterwards there is nowhere to get it.
            String animalRecordNumber;
            try (PreparedStatement lookup = conn.prepareStatement(
                    "SELECT animal_record_number FROM vaccines WHERE id = ?")) {
                lookup.setString(1, id);
                try (ResultSet rs = lookup.executeQuery()) {
                    if (!rs.next()) {
                        throw new Exception("No vaccine found with the provided ID.");
                    }
                    animalRecordNumber = rs.getString(1);
                }
            }
            try (PreparedStatement tombstone = conn.prepareStatement(
                    "INSERT OR REPLACE INTO deleted_vaccines (id, animal_record_number) VALUES (?, ?)")) {
                tombstone.setString(1, id);
                tombstone.setString(2, animalRecordNumber);
                tombstone.executeUpdate();
            }
            try (PreparedStatement delete = conn.prepareStatement("DELETE FROM vaccines WHERE id = ?")) {
                delete.setString(1, id);
                delete.executeUpdate();
            }
            return null;
        });
    }

    // -------------------------------------------------------------------------
    //  Synchronisation
    // -------------------------------------------------------------------------

    @Override
    public List<Vaccine> getAllUnsyncedVaccines() throws Exception {
        return queryVaccines("SELECT * FROM vaccines WHERE synced = 0");
    }

    @Override
    public List<Vaccine> getAllVaccines() throws Exception {
        return queryVaccines("SELECT * FROM vaccines");
    }

    @Override
    public void saveFromRemote(List<Vaccine> vaccines, Map<String, RowVersion> expected) throws Exception {
        if (vaccines.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO vaccines (" + COLUMNS + ") " + VALUES + """

                ON CONFLICT(id) DO UPDATE SET
                    animal_record_number = excluded.animal_record_number,
                    vaccine_name = excluded.vaccine_name,
                    vaccination_date = excluded.vaccination_date,
                    synced = excluded.synced,
                    last_modified = excluded.last_modified
                WHERE vaccines.last_modified IS ? AND vaccines.synced IS ?
                """;
        Transactions.inTransaction(dataSource, conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (Vaccine vaccine : vaccines) {
                    bind(pstmt, vaccine);
                    RowVersionGuard.bind(pstmt, 7, expected.get(vaccine.getId()));
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }
            return null;
        });
    }

    /** Only rows that were synced when read and have not changed since. */
    @Override
    public void deleteRemovedRemotely(Map<String, RowVersion> expected) throws Exception {
        if (expected.isEmpty()) {
            return;
        }
        Transactions.inTransaction(dataSource, conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "DELETE FROM vaccines WHERE id = ? AND synced = 1 AND last_modified IS ? AND synced IS ?")) {
                for (Map.Entry<String, RowVersion> entry : expected.entrySet()) {
                    pstmt.setString(1, entry.getKey());
                    RowVersionGuard.bind(pstmt, 2, entry.getValue());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }
            return null;
        });
    }

    @Override
    public int markSynced(List<Vaccine> pushed) throws Exception {
        if (pushed.isEmpty()) {
            return 0;
        }
        // IS rather than = so that NULL matches NULL.
        String sql = """
                UPDATE vaccines SET synced = 1
                WHERE id = ? AND animal_record_number IS ? AND vaccine_name IS ?
                  AND vaccination_date IS ? AND last_modified IS ?
                """;
        return Transactions.inTransaction(dataSource, conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (Vaccine vaccine : pushed) {
                    pstmt.setString(1, vaccine.getId());
                    pstmt.setString(2, vaccine.getAnimalRecordNumber());
                    pstmt.setString(3, vaccine.getVaccineName());
                    pstmt.setString(4, vaccine.getVaccinationDate());
                    pstmt.setString(5, vaccine.getLastModified());
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

    @Override
    public Map<String, String> getPendingDeletions() throws Exception {
        Map<String, String> pending = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT id, animal_record_number FROM deleted_vaccines ORDER BY deleted_at");
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                pending.put(rs.getString(1), rs.getString(2));
            }
        }
        return pending;
    }

    /**
     * Only once the deletion has been applied remotely. Dropping a tombstone
     * earlier would let the record come back on the next pull.
     */
    @Override
    public void clearPendingDeletions(Collection<String> vaccineIds) throws Exception {
        executeForEach("DELETE FROM deleted_vaccines WHERE id = ?", vaccineIds);
    }

    private void executeForEach(String sql, Collection<String> ids) throws Exception {
        if (ids.isEmpty()) {
            return;
        }
        Transactions.inTransaction(dataSource, conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (String id : ids) {
                    pstmt.setString(1, id);
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }
            return null;
        });
    }

    private List<Vaccine> queryVaccines(String sql, String... params) throws Exception {
        List<Vaccine> vaccines = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                pstmt.setString(i + 1, params[i]);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    vaccines.add(mapResultSetToVaccine(rs));
                }
            }
        } catch (SQLException e) {
            throw new Exception("Error retrieving vaccines", e);
        }
        return vaccines;
    }

    /** Fills parameters 1-6 in {@link #COLUMNS} order. */
    private static void bind(PreparedStatement pstmt, Vaccine vaccine) throws SQLException {
        pstmt.setString(1, vaccine.getId());
        pstmt.setString(2, vaccine.getAnimalRecordNumber());
        pstmt.setString(3, vaccine.getVaccineName());
        pstmt.setString(4, vaccine.getVaccinationDate());
        pstmt.setInt(5, vaccine.isSynced() ? 1 : 0);
        String lastModified = vaccine.getLastModified();
        pstmt.setString(6, (lastModified == null || lastModified.isBlank()) ? null : lastModified);
    }

    private Vaccine mapResultSetToVaccine(ResultSet rs) throws SQLException {
        Vaccine vaccine = Vaccine.fromExistingRecord(rs.getString("id"));
        vaccine.setAnimalRecordNumber(rs.getString("animal_record_number"));
        vaccine.setVaccineName(rs.getString("vaccine_name"));
        vaccine.setVaccinationDate(rs.getString("vaccination_date"));
        vaccine.setSynced(rs.getInt("synced") == 1);
        vaccine.setLastModified(rs.getString("last_modified"));
        return vaccine;
    }
}
