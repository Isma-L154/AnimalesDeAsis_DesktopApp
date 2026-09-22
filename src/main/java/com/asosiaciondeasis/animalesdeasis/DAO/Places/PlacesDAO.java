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
    public List<Place> getAllPlaces() throws Exception {
        List<Place> places = new ArrayList<>();
        String sql = """
                SELECT p.id, p.name, pr.name AS province_name
                FROM places p
                JOIN provinces pr ON p.province_id = pr.id
                ORDER BY p.name
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                places.add(new Place(rs.getInt("id"), rs.getString("name"), rs.getString("province_name")));
            }
        }
        return places;
    }
}
