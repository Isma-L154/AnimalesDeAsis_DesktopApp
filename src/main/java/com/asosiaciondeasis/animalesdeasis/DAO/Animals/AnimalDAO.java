package com.asosiaciondeasis.animalesdeasis.DAO.Animals;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Animals.IAnimalDAO;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Config.DatabaseConnection;

import java.sql.*;
import java.util.List;
import java.util.ArrayList;

public class AnimalDAO implements IAnimalDAO {


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
    try(Connection conn = DatabaseConnection.getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)){

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
        String sql = "SELECT * FROM animals";

        try(Connection conn = DatabaseConnection.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery()){

            /**
             * Iterate through the ResultSet, create Animal objects from each row,
             * populate their fields with the corresponding column data,
             * and add each Animal to the list.
             * */

            while (rs.next()) {
                Animal animal = new Animal(rs.getString("record_number"));

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

                animals.add(animal);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            throw new Exception("Error inserting animal", e);
        }

        return animals;
    }

    @Override
    public Animal findByChipNumber(String chipNumber) {
        return null;
    }

    @Override
    public Animal findByBarcode(String barcode) {
        return null;
    }

    @Override
    public List<Animal> findByFilters(String species, String startDate, String endDate, Boolean adopted) {
        return List.of();
    }

    @Override
    public void updateAnimal(Animal animal) throws Exception {

    }

    @Override
    public void deleteAnimal(String recordNumber) throws Exception {

    }
}
