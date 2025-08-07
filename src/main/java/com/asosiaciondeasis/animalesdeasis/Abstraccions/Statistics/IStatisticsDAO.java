package com.asosiaciondeasis.animalesdeasis.Abstraccions.Statistics;

import java.util.Map;

public interface IStatisticsDAO {
    /**
     * //TODO Add more statistics if needed
     */
    Map<String, Integer> getMonthlyAdmissions(int year) throws Exception;

    int getTotalAdmissions(int year) throws Exception;

    double getAdoptionRate(int year) throws Exception;
}
