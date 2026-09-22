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

import javax.sql.DataSource;

/**
 * Composition root: wires services to their DAOs over the application's
 * database. Controllers ask for abstractions here instead of building
 * implementations themselves.
 */
public final class ServiceFactory {

    private static final DataSource DATA_SOURCE = Database.dataSource();

    private ServiceFactory() {
    }

    public static IAnimalService getAnimalService() {
        return new AnimalService(new AnimalDAO(DATA_SOURCE));
    }

    public static IVaccineService getVaccineService() {
        return new VaccineService(new VaccineDAO(DATA_SOURCE));
    }

    public static IStatisticsService getStatisticsService() {
        return new StatisticsService(new StatisticsDAO(DATA_SOURCE));
    }

    public static IPlacesService getPlaceService() {
        return new PlaceService(new PlacesDAO(DATA_SOURCE));
    }

    public static ShelterSummaryService getShelterSummaryService() {
        return new ShelterSummaryService(new AnimalDAO(DATA_SOURCE));
    }

    public static SyncService getSyncService() {
        return new SyncService(DATA_SOURCE);
    }

    public static CsvStatisticsExporter getCsvStatisticsExporter() {
        return new CsvStatisticsExporter(new StatisticsDAO(DATA_SOURCE));
    }
}
