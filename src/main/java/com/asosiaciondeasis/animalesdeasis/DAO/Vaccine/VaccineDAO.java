package com.asosiaciondeasis.animalesdeasis.DAO.Vaccine;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Vaccines.IVaccineDAO;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VaccineDAO implements IVaccineDAO {
    private static final Logger log = LoggerFactory.getLogger(VaccineDAO.class);


    private final Connection conn;

    public VaccineDAO(Connection conn) {
        this.conn = conn;
    }

    @Override
    public void insertVaccine(Vaccine vaccine) throws Exception {
        String sql;
        //We have to separate the SQL query into two different queries, one with the last_modified field and another without it.
        if (vaccine.getLastModified() != null && !vaccine.getLastModified().trim().isEmpty()) {
            sql = """
            INSERT INTO vaccines (id, animal_record_number, vaccine_name, vaccination_date, synced, last_modified)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        } else {
            sql = """
            INSERT INTO vaccines (id, animal_record_number, vaccine_name, vaccination_date, synced)
            VALUES (?, ?, ?, ?, ?)
        """;
        }

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, vaccine.getId());
            pstmt.setString(2, vaccine.getAnimalRecordNumber());
            pstmt.setString(3, vaccine.getVaccineName());
            pstmt.setString(4, vaccine.getVaccinationDate());
            pstmt.setInt(5, vaccine.isSynced() ? 1 : 0);

            if (vaccine.getLastModified() != null && !vaccine.getLastModified().trim().isEmpty()) {
                pstmt.setString(6, vaccine.getLastModified());
            }

            pstmt.executeUpdate();
            log.info("Vaccine inserted successfully.");

        } catch (SQLException e) {
            throw new Exception("Error inserting vaccine", e);
        }
    }

    @Override
    public List<Vaccine> getVaccinesByAnimal(String animalRecordNumber) throws Exception {
        List<Vaccine> vaccines = new ArrayList<>();
        String sql = "SELECT * FROM vaccines WHERE animal_record_number = ? ORDER BY vaccination_date DESC";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

            /**
             * Set the parameter for the prepared statement with the animal's record number
             * And execute the query and get the result set
             * */
            pstmt.setString(1, animalRecordNumber);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                vaccines.add(mapResultSetToVaccine(rs));
            }

        } catch (SQLException e) {
            throw new Exception("Error retrieving vaccines", e);
        }

        return vaccines;
    }

    /**
     * The reason we have this method with a timestamp, It's because we want to update the last_modified field
     * every time we update an animal, so we can keep track of when the last modification.
     * But at the same time, when we pull the data from the database, we don't want to update the last_modified field
     * because we are just reading the data, not modifying it. So we have this boolean parameter to do that
     * */
    @Override
    public void updateVaccine(Vaccine vaccine, boolean timestamp) throws Exception {
        String sql;
        if (timestamp) {
            sql = """
            UPDATE vaccines
            SET vaccine_name = ?, vaccination_date = ?, synced = ?, last_modified = datetime('now', 'utc')
            WHERE id = ?
        """;
        } else {
            sql = """
            UPDATE vaccines
            SET vaccine_name = ?, vaccination_date = ?, synced = ?, last_modified = ?
            WHERE id = ?
        """;
        }

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, vaccine.getVaccineName());
            pstmt.setString(2, vaccine.getVaccinationDate());
            pstmt.setInt(3, vaccine.isSynced() ? 1 : 0);

            if (timestamp) {
                pstmt.setString(4, vaccine.getId());
            } else {
                pstmt.setString(4, vaccine.getLastModified());
                pstmt.setString(5, vaccine.getId());
            }

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new Exception("No vaccine found with ID: " + vaccine.getId());
            }

            log.info("Vaccine updated successfully.");
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
     * long enough to reach Firebase.</p>
     *
     * <p>Without it, deleting a vaccine offline left nothing behind, so the next
     * pull found the row still in Firebase, saw nothing locally, and treated it
     * as new. The record came back days later with no explanation.</p>
     *
     * <p>Both statements run in one transaction. A row removed without its
     * tombstone written is exactly the bug this is here to fix, so they cannot be
     * allowed to come apart.</p>
     */
    @Override
    public void deleteVaccine(String id) throws Exception {
        // Read the owning animal before the row goes: the tombstone needs it to
        // address the remote document, and afterwards there is nowhere to get it.
        String animalRecordNumber = null;
        try (PreparedStatement lookup = conn.prepareStatement(
                "SELECT animal_record_number FROM vaccines WHERE id = ?")) {
            lookup.setString(1, id);
            try (ResultSet rs = lookup.executeQuery()) {
                if (rs.next()) {
                    animalRecordNumber = rs.getString("animal_record_number");
                }
            }
        }
        if (animalRecordNumber == null) {
            throw new Exception("No vaccine found with the provided ID.");
        }

        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            try (PreparedStatement tombstone = conn.prepareStatement(
                    "INSERT OR REPLACE INTO deleted_vaccines (id, animal_record_number) VALUES (?, ?)")) {
                tombstone.setString(1, id);
                tombstone.setString(2, animalRecordNumber);
                tombstone.executeUpdate();
            }
            try (PreparedStatement delete = conn.prepareStatement(
                    "DELETE FROM vaccines WHERE id = ?")) {
                delete.setString(1, id);
                if (delete.executeUpdate() == 0) {
                    throw new Exception("No vaccine found with the provided ID.");
                }
            }
            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            throw new Exception("Error deleting vaccine", e);
        } finally {
            conn.setAutoCommit(previousAutoCommit);
        }
    }

    @Override
    public List<String> getPendingDeletions() throws Exception {
        List<String> ids = new ArrayList<>();
        try (PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT id FROM deleted_vaccines ORDER BY deleted_at");
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getString("id"));
            }
        }
        return ids;
    }

    @Override
    public String getPendingDeletionAnimal(String vaccineId) throws Exception {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT animal_record_number FROM deleted_vaccines WHERE id = ?")) {
            pstmt.setString(1, vaccineId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getString("animal_record_number") : null;
            }
        }
    }

    /**
     * Removes a tombstone once the deletion has been applied remotely.
     *
     * <p>Only then. Dropping it earlier would restore the original bug, with the
     * deletion lost and the record free to return on the next pull.</p>
     */
    @Override
    public void clearPendingDeletion(String vaccineId) throws Exception {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "DELETE FROM deleted_vaccines WHERE id = ?")) {
            pstmt.setString(1, vaccineId);
            pstmt.executeUpdate();
        }
    }

    @Override
    public List<Vaccine> getAllUnsyncedVaccines() throws Exception {
        List<Vaccine> vaccines = new ArrayList<>();
        String sql = "SELECT * FROM vaccines WHERE synced = 0";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                vaccines.add(mapResultSetToVaccine(rs));
            }
        } catch (SQLException e) {
            throw new Exception("Error retrieving all unsynced vaccines", e);
        }

        return vaccines;
    }

    @Override
    public Vaccine existsVaccine(String id) throws Exception {
        String sql = "SELECT * FROM vaccines WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToVaccine(rs);
            }
        }
        return null;
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

