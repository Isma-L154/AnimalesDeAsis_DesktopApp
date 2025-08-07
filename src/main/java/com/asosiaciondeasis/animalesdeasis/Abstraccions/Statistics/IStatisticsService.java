package com.asosiaciondeasis.animalesdeasis.Abstraccions.Statistics;

import java.util.Map;

public interface IStatisticsService {
    Map<String, Integer> getMonthlyAdmissions(int year) throws Exception;

    int getTotalAdmissions(int year) throws Exception;

    double getAdoptionRate(int year) throws Exception;
}
