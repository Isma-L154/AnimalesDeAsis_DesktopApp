package com.asosiaciondeasis.animalesdeasis.DAO.Animals;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Animals.IAnimalDAO;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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
     * @throws SQLException if any, database error occurs during insertion.
     */

    @Override
    public boolean insertAnimal(Animal animal) throws Exception {
        String sql;
        //We have to separate the SQL query into two different queries, one with the last_modified field and another without it.
        if (animal.getLastModified() != null && !animal.getLastModified().trim().isEmpty()) {
            sql = """
            INSERT INTO animals (
                record_number, chip_number, barcode, admission_date,
                collected_by, place_id, reason_for_rescue, species,
                approximate_age, sex, name, ailments, neutering_date, adopted,
                synced, active, last_modified
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        } else {
            sql = """
            INSERT INTO animals (
                record_number, chip_number, barcode, admission_date,
                collected_by, place_id, reason_for_rescue, species,
                approximate_age, sex, name, ailments, neutering_date, adopted,
                synced, active
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        }

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

            if (animal.getLastModified() != null && !animal.getLastModified().trim().isEmpty()) {
                pstmt.setString(17, animal.getLastModified());
            }

            pstmt.executeUpdate();
            System.out.println("✅ Animal inserted successfully.");
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Retrieves all animals from the database.
     *
     * @return A list of Animal objects representing every record in the animal table.
     * @throws SQLException if there is any error during database interaction.
     */

    @Override
    public List<Animal> getAllAnimals() throws Exception {
        List<Animal> animals = new ArrayList<>();
        String sql = "SELECT * FROM animals WHERE active = 1 ORDER BY admission_date DESC";

        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

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
    public Animal findByRecordNumber(String recordNumber) throws Exception {
        String sql = "SELECT * FROM animals WHERE record_number = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, recordNumber);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToAnimal(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * The dates in this Method should be in YYY-MM-DD Format --> I do the conversion in the Util Package called DateUtil.
     * This method already receives the date in the correct format, in the Service(BL) package
     */
    @Override
    public List<Animal> findByFilters(String species, String startDate, String endDate, String chipNumber ,Boolean showInactive) throws Exception {
        List<Animal> animals = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM animals WHERE 1=1");

        // Check if we want to show inactive animals
        if (showInactive != null && showInactive) {
            sql.append(" AND active = 0");
        } else {
            sql.append(" AND active = 1");
        }

        // Build dynamic WHERE clauses
        if (species != null && !species.isBlank()) {
            sql.append(" AND species = ?");
        }

        if (startDate != null && endDate != null) {
            sql.append(" AND admission_date BETWEEN ? AND ?");
        }

        if (chipNumber != null && !chipNumber.isBlank()) {
            sql.append(" AND chip_number LIKE ?");
        }

        sql.append(" ORDER BY admission_date DESC");

        // Prepare the statement with the dynamic SQL
        try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            int index = 1;

            if (species != null && !species.isBlank()) {
                pstmt.setString(index++, species);
            }

            if (startDate != null && endDate != null) {
                pstmt.setString(index++, startDate);
                pstmt.setString(index++, endDate);
            }

            if (chipNumber != null && !chipNumber.isBlank()) {
                pstmt.setString(index++, "%" + chipNumber + "%");
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

    /**
     * The reason we have this method with a timestamp, It's because we want to update the last_modified field
     * every time we update an animal, so we can keep track of when the last modification.
     * But at the same time, when we pull the data from the database, we don't want to update the last_modified field
     * because we are just reading the data, not modifying it. So we have this boolean parameter to do that
     * */

    @Override
    public boolean updateAnimal(Animal animal, boolean timestamp) throws Exception {
        String updateSql;
        if (timestamp) {
            updateSql = """
            UPDATE animals
            SET chip_number = ?, barcode = ?, admission_date = ?, collected_by = ?, place_id = ?,
                reason_for_rescue = ?, species = ?, approximate_age = ?, sex = ?, name = ?,
                ailments = ?, neutering_date = ?, adopted = ?, active = ?, synced = ?,
                last_modified = datetime('now', 'utc')
            WHERE record_number = ?
        """;
        } else {
            updateSql = """
            UPDATE animals
            SET chip_number = ?, barcode = ?, admission_date = ?, collected_by = ?, place_id = ?,
                reason_for_rescue = ?, species = ?, approximate_age = ?, sex = ?, name = ?,
                ailments = ?, neutering_date = ?, adopted = ?, active = ?, synced = ?,
                last_modified = ?
            WHERE record_number = ?
        """;
        }

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
            pstmt.setInt(14, animal.isActive() ? 1 : 0);
            pstmt.setInt(15, animal.isSynced() ? 1 : 0);

            if (timestamp) {
                pstmt.setString(16, animal.getRecordNumber());
            } else {
                pstmt.setString(16, animal.getLastModified());
                pstmt.setString(17, animal.getRecordNumber());
            }

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new Exception("⚠️ No active animal found with recordNumber: " + animal.getRecordNumber());
            }

            System.out.println("Animal updated successfully.");
            return true;
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE constraint failed")) {
                throw new Exception("❌ chip_number or barcode must be unique", e);
            }
            throw new Exception("Error updating animal -> ", e);
        }
    }

    /**
     * In this method, we are using a LOGIC delete
     */
    @Override
    public void deleteAnimal(String recordNumber) throws Exception {
        String sql = "UPDATE animals SET active = 0, synced = 0, last_modified = datetime('now', 'utc') WHERE record_number = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
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

        String sql = "UPDATE animals SET active = 1, synced = 0, last_modified = datetime('now', 'utc') WHERE record_number = ?";
        
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
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

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
     * Private method to map the info of the animal, it is used in every method of the class that his purpose is
     * to search for a specific animal.
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
        animal.setActive(rs.getInt("active") == 1);
        animal.setLastModified(rs.getString("last_modified"));
        return animal;
    }

}
