package com.asosiaciondeasis.animalesdeasis.DAO.Places;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Places.IPlaceDAO;
import com.asosiaciondeasis.animalesdeasis.Model.Place;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class PlacesDAO implements IPlaceDAO {

    private final Connection conn;

    public PlacesDAO(Connection conn) {
        this.conn = conn;
    }

    @Override
    public List<Place> getAllPlaces() {
        List<Place> places = new ArrayList<>();
        String sql = "SELECT id, name FROM places ORDER BY name";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Place place = new Place(
                        rs.getInt("id"),
                        rs.getString("name")
                );
                places.add(place);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return places;
    }
}
