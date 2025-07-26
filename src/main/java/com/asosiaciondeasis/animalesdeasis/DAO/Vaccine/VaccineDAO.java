package com.asosiaciondeasis.animalesdeasis.DAO.Vaccine;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Vaccines.IVaccineDAO;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class VaccineDAO implements IVaccineDAO {

    private final Connection conn;

    public VaccineDAO(Connection conn) {
        this.conn = conn;
    }

    @Override
    public void insertVaccine(Vaccine vaccine) throws Exception {
        String sql = """
                    INSERT INTO vaccines (animal_record_number, vaccine_name, vaccination_date)
                    VALUES (?, ?, ?)
                """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, vaccine.getAnimalRecordNumber());
            pstmt.setString(2, vaccine.getVaccineName());
            pstmt.setString(3, vaccine.getVaccinationDate());

            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
            throw new Exception("Error inserting vaccine", e);
        }
    }

    @Override
    public List<Vaccine> getVaccinesByAnimal(String animalRecordNumber) throws Exception {
        List<Vaccine> vaccines = new ArrayList<>();
        String sql = "SELECT id, animal_record_number, vaccine_name, vaccination_date FROM vaccines WHERE animal_record_number = ?";

        try(PreparedStatement pstmt = conn.prepareStatement(sql)){

            /**
             * Set the parameter for the prepared statement with the animal's record number
             * And execute the query and get the result set
             * */
            pstmt.setString(1, animalRecordNumber);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Vaccine vaccine = mapResultSetToVaccine(rs);
                vaccines.add(vaccine);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            throw new Exception("Error retrieving vaccines", e);
        }

        return vaccines;
    }

    /**
     * We have the Update in case there's a mistake in the name or the date, allowing the user
     * to make corrections without having to delete and reinsert the record.

     * However, we are deliberately omitting traceability (e.g., edit history or audit logs)
     *  to keep the system lightweight and simple.
     * */

    @Override
    public void updateVaccine(Vaccine vaccine) throws Exception {
        String sql = """
        UPDATE vaccines
        SET vaccine_name = ?, vaccination_date = ?
        WHERE id = ?
    """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, vaccine.getVaccineName());
            pstmt.setString(2, vaccine.getVaccinationDate());
            pstmt.setInt(3, vaccine.getId());

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new Exception("No vaccine found with ID: " + vaccine.getId());
            }


        } catch (SQLException e) {
            e.printStackTrace();
            throw new Exception("Error updating vaccine", e);
        }
    }

    /**
     * This DOES NOT work as a Logic Delete
     * */
    @Override
    public void deleteVaccine(int id) throws Exception {
        String sql = "DELETE FROM vaccines WHERE id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
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
    public List<Vaccine> getUnsyncedVaccinesByAnimal(String animalRecordNumber) throws Exception {
        List<Vaccine> vaccines = new ArrayList<>();
        String sql = "SELECT * FROM vaccines WHERE animal_record_number = ? AND synced = 0";

        try(PreparedStatement pstmt = conn.prepareStatement(sql)){

            pstmt.setString(1, animalRecordNumber);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Vaccine vaccine = mapResultSetToVaccine(rs);
                vaccines.add(vaccine);
            }
        }
        return vaccines;
    }

    private Vaccine mapResultSetToVaccine(ResultSet rs) throws SQLException {
        Vaccine vaccine = new Vaccine(
                rs.getInt("id"),
                rs.getString("animal_record_number"),
                rs.getString("vaccine_name"),
                rs.getString("vaccination_date")
        );
        vaccine.setSynced(rs.getInt("synced") == 1);
        return vaccine;
    }
}

