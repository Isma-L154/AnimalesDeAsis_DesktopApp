package com.asosiaciondeasis.animalesdeasis.DAO.Vaccine;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Vaccines.IVaccineDAO;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class VaccineDAO implements IVaccineDAO {

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
            System.out.println("✅ Vaccine inserted successfully.");

        } catch (SQLException e) {
            e.printStackTrace();
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
            e.printStackTrace();
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
        String timestampClause = timestamp ?
                ", last_modified = strftime('%Y-%m-%dT%H:%M:%S', 'now')" :
                "";

        String sql = """
        UPDATE vaccines
        SET vaccine_name = ?, vaccination_date = ?, synced = ?""" + timestampClause + """
        WHERE id = ?
    """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, vaccine.getVaccineName());
            pstmt.setString(2, vaccine.getVaccinationDate());
            pstmt.setInt(3, vaccine.isSynced() ? 1 : 0);
            pstmt.setString(4, vaccine.getId());

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new Exception("No vaccine found with ID: " + vaccine.getId());
            }

            System.out.println("Vaccine updated successfully.");
        } catch (SQLException e) {
            e.printStackTrace();
            throw new Exception("Error updating vaccine", e);
        }
    }

    /**
     * This DOES NOT work as a Logic Delete
     */
    @Override
    public void deleteVaccine(String id) throws Exception {
        String sql = "DELETE FROM vaccines WHERE id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected == 0) {
                throw new Exception("⚠️ No vaccine found with the provided ID.");
            }

            System.out.println("✅ Vaccine deleted successfully.");
        } catch (SQLException e) {
            e.printStackTrace();
            throw new Exception("Error deleting vaccine", e);
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
            e.printStackTrace();
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

