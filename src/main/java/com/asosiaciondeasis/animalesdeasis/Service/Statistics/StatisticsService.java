package com.asosiaciondeasis.animalesdeasis.Service.Statistics;


import com.asosiaciondeasis.animalesdeasis.Abstraccions.Statistics.IStatisticsDAO;
import com.asosiaciondeasis.animalesdeasis.Abstraccions.Statistics.IStatisticsService;

import java.util.Map;

public class StatisticsService implements IStatisticsService {

    private final IStatisticsDAO statisticsDAO;

    public StatisticsService(IStatisticsDAO statisticsDAO) {
        this.statisticsDAO = statisticsDAO;
    }

    @Override
    public Map<String, Integer> getMonthlyAdmissions(int year) throws Exception {
        return statisticsDAO.getMonthlyAdmissions(year);
    }

    @Override
    public int getTotalAdmissions(int year) throws Exception {
        return statisticsDAO.getTotalAdmissions(year);
    }

    @Override
    public double getAdoptionRate(int year) throws Exception {
        return statisticsDAO.getAdoptionRate(year);
    }
}
