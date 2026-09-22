package com.asosiaciondeasis.animalesdeasis.Config;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Animals.IAnimalService;
import com.asosiaciondeasis.animalesdeasis.Abstraccions.Places.IPlacesService;
import com.asosiaciondeasis.animalesdeasis.Abstraccions.Statistics.IStatisticsService;
import com.asosiaciondeasis.animalesdeasis.Abstraccions.Vaccines.IVaccineService;
import com.asosiaciondeasis.animalesdeasis.DAO.Animals.AnimalDAO;
import com.asosiaciondeasis.animalesdeasis.DAO.Places.PlacesDAO;
import com.asosiaciondeasis.animalesdeasis.DAO.Statistics.StatisticsDAO;
import com.asosiaciondeasis.animalesdeasis.DAO.Vaccine.VaccineDAO;
import com.asosiaciondeasis.animalesdeasis.Service.Animal.AnimalService;
import com.asosiaciondeasis.animalesdeasis.Service.Home.ShelterSummaryService;
import com.asosiaciondeasis.animalesdeasis.Service.Place.PlaceService;
import com.asosiaciondeasis.animalesdeasis.Service.Statistics.StatisticsService;
import com.asosiaciondeasis.animalesdeasis.Service.SyncService;
import com.asosiaciondeasis.animalesdeasis.Service.Vaccine.VaccineService;
import com.asosiaciondeasis.animalesdeasis.Util.Exporters.CsvStatisticsExporter;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Composition root: wires services to their DAOs over the shared database
 * connection. Controllers ask for abstractions here instead of building
 * implementations themselves.
 */
public final class ServiceFactory {

    private static final Connection conn;

    static {
        try {
            conn = DatabaseConnection.getConnection();
        } catch (SQLException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private ServiceFactory() {
    }

    public static IAnimalService getAnimalService() {
        return new AnimalService(new AnimalDAO(conn));
    }

    public static IVaccineService getVaccineService() {
        return new VaccineService(new VaccineDAO(conn));
    }

    public static IStatisticsService getStatisticsService() {
        return new StatisticsService(new StatisticsDAO(conn));
    }

    public static IPlacesService getPlaceService() {
        return new PlaceService(new PlacesDAO(conn));
    }

    public static ShelterSummaryService getShelterSummaryService() {
        return new ShelterSummaryService(new AnimalDAO(conn));
    }

    public static SyncService getSyncService() {
        return new SyncService(conn);
    }

    public static CsvStatisticsExporter getCsvStatisticsExporter() {
        return new CsvStatisticsExporter(new StatisticsDAO(conn));
    }
}
