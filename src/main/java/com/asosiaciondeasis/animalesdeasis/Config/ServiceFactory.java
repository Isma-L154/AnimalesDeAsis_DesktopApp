package com.asosiaciondeasis.animalesdeasis.Config;

import com.asosiaciondeasis.animalesdeasis.DAO.Animals.AnimalDAO;
import com.asosiaciondeasis.animalesdeasis.DAO.Places.PlacesDAO;
import com.asosiaciondeasis.animalesdeasis.DAO.Statistics.StatisticsDAO;
import com.asosiaciondeasis.animalesdeasis.DAO.Vaccine.VaccineDAO;
import com.asosiaciondeasis.animalesdeasis.Service.Animal.AnimalService;
import com.asosiaciondeasis.animalesdeasis.Service.Place.PlaceService;
import com.asosiaciondeasis.animalesdeasis.Service.Statistics.StatisticsService;
import com.asosiaciondeasis.animalesdeasis.Service.Vaccine.VaccineService;

import java.sql.Connection;
import java.sql.SQLException;

public class ServiceFactory {

    private static Connection conn;

    static {
        try {
            conn = DatabaseConnection.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to establish database connection", e);
        }
    }

    public static AnimalService getAnimalService() {
        return new AnimalService(new AnimalDAO(conn));
    }

    public static VaccineService getVaccineService() {
        return new VaccineService(new VaccineDAO(conn));
    }

    public static StatisticsService getStatisticsService() {
        return new StatisticsService(new StatisticsDAO(conn));
    }

    public static PlaceService getPlaceService() {return new PlaceService(new PlacesDAO(conn));}
}
