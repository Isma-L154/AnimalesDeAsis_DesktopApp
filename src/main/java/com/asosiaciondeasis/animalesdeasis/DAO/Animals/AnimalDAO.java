package com.asosiaciondeasis.animalesdeasis.DAO.Animals;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Animals.IAnimalDAO;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;

import java.sql.*;
import java.util.List;
import java.util.ArrayList;

public class AnimalDAO implements IAnimalDAO {

    //This value is for DI (Dependency injection), makes it easier to change the DB if needed
    private final Connection conn;

    public AnimalDAO(Connection conn) {
        this.conn = conn;
    }

    /**
     * Inserts a new Animal record into the database.
     * The record_number (UUID) is set in the Animal Model (Constructor) before calling this method
     *
     * @param animal The Animal object containing all the data to insert.
     * @throws SQLException if any database error occurs during insertion.
     */

    @Override
    public void insertAnimal(Animal animal) throws Exception {
        String sql = """
            INSERT INTO animals (
                record_number, chip_number, barcode, admission_date,
                collected_by, place_id, reason_for_rescue, species,
                approximate_age, sex, name, ailments, neutering_date, adopted
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    try(PreparedStatement pstmt = conn.prepareStatement(sql)){

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

        pstmt.executeUpdate();
        System.out.println("✅ Animal inserted successfully.");

    } catch (SQLException e) {
        e.printStackTrace();
        throw new Exception("Error inserting animal", e);
    }

    }

    /**
     * Retrieves all animals from the database.
     *
     * @return A list of Animal objects representing every record in the animals table.
     * @throws SQLException if there is any error during database interaction.
     */

    @Override
    public List<Animal> getAllAnimals() throws Exception {
        List<Animal> animals = new ArrayList<>();
        String sql = "SELECT * FROM animals WHERE active = 1;";

        try(PreparedStatement pstmt = conn.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery()){

            /**
             * Iterate through the ResultSet, create Animal objects from each row,
             * populate their fields with the corresponding column data,
             * and add each Animal to the list.
             * */

            while (rs.next()) {
                animals.add(mapResultSetToAnimal(rs));
            }

        } catch (SQLException e) {
            e.printStackTrace();
            throw new Exception("Error getting animal", e);
        }

        return animals;
    }

    @Override
    public Animal findByChipNumber(String chipNumber) throws Exception {

        String sql = "SELECT * FROM animals WHERE chip_number = ? AND active = 1";
        try(PreparedStatement pstmt = conn.prepareStatement(sql)){
            pstmt.setString(1, chipNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToAnimal(rs);
                }
            }
        } catch (SQLException e) {
            throw new Exception("Error finding animal by chip number", e);
        }
        return null; //No animal found
    }

    @Override
    public Animal findByBarcode(String barcode) throws Exception {

        String sql = "SELECT * FROM animals WHERE barcode = ? AND active = 1";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, barcode);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToAnimal(rs);
                }
            }
        } catch (SQLException e) {
            throw new Exception("Error finding animal by barcode", e);
        }

        return null; //No animal found
    }

    /**
     * The dates in this Method should be in YYY-MM-DD Format --> I do the conversion in the Util Package called DateUtil.
     * This method already receives the date in the correct format, in the Service(BL) package
     */
    @Override
    public List<Animal> findByFilters(String species, String startDate, String endDate, Boolean adopted) throws Exception {
        List<Animal> animals = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM animals WHERE active = 1");

        // Build dynamic WHERE clauses
        if (species != null && !species.isBlank()) {
            sql.append(" AND species = ?");
        }

        if (startDate != null && endDate != null) {
            sql.append(" AND admission_date BETWEEN ? AND ?");
        }

        if (adopted != null) {
            sql.append(" AND adopted = ?");
        }

        try(PreparedStatement pstmt = conn.prepareStatement(sql.toString())){
            int index = 1;

            if (species != null && !species.isBlank()) {
                pstmt.setString(index++, species);
            }

            if (startDate != null && endDate != null) {
                pstmt.setString(index++, startDate);  // Already ISO format
                pstmt.setString(index++, endDate);
            }

            if (adopted != null) {
                pstmt.setInt(index++, adopted ? 1 : 0);
            }

            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                animals.add(mapResultSetToAnimal(rs));
            }


        } catch (SQLException e) {
            throw new Exception("Error fetching animals by filters", e);
        }

        return animals;
    }


    @Override
    public void updateAnimal(Animal animal) throws Exception {
        String updateSql = """
        UPDATE animals SET
            chip_number = ?, barcode = ?, admission_date = ?,
            collected_by = ?, place_id = ?, reason_for_rescue = ?,
            species = ?, approximate_age = ?, sex = ?, name = ?,
            ailments = ?, neutering_date = ?, adopted = ?
        WHERE record_number = ? AND active = 1
    """;

        try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {

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
            pstmt.setString(14, animal.getRecordNumber());

            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected == 0) {
                throw new Exception("⚠️ No active animal found with recordNumber: " + animal.getRecordNumber());
            }

            System.out.println("Animal updated successfully.");
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE constraint failed")) {
                throw new Exception("❌ chip_number or barcode must be unique", e);
            }
            throw new Exception("Error updating animal -> ", e);
        }

    }
    /**
     * In this delete we are using a LOGIC delete
     * */
    @Override
    public void deleteAnimal(String recordNumber) throws Exception {
        String sql = "UPDATE animals SET active = 0 WHERE record_number = ?";

        try(PreparedStatement pstmt = conn.prepareStatement(sql)){
            pstmt.setString(1, recordNumber);
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows == 0) {
                throw new Exception("❌ No animal found with the given record number.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new Exception("Error performing logical delete", e);
        }
    }

    @Override
    public void reactivateAnimal(String recordNumber) throws Exception {

        String sql = "UPDATE animals SET active = 1 WHERE record_number = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, recordNumber);
            int rows = pstmt.executeUpdate();
            if (rows == 0) {
                throw new Exception("No animal found to reactivate.");
            }
        } catch (SQLException e) {
            throw new Exception("Error reactivating animal", e);
        }
    }

    @Override
    public List<Animal> getUnsyncedAnimals() throws Exception {
        List<Animal> unsyncedAnimals = new ArrayList<>();
        String sql = "SELECT * FROM animals WHERE synced = 0";
        try(PreparedStatement pstmt = conn.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery()){

            while (rs.next()) {
                unsyncedAnimals.add(mapResultSetToAnimal(rs));
            }

        } catch (SQLException e) {
            e.printStackTrace();
            throw new Exception("Error getting unsynced animals", e);
        }
        return unsyncedAnimals;
    }

    /**
     *  Private method to map the info of the animal, it is used in every method of the class, that his purpose is
     *  to search for a specific animal.
     */
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

        return animal;
    }
}
