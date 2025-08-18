package com.asosiaciondeasis.animalesdeasis.DAO.Statistics;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Statistics.IStatisticsDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;


public class StatisticsDAO implements IStatisticsDAO {

    private final Connection conn;

    public StatisticsDAO(Connection conn) {
        this.conn = conn;
    }


    @Override
    public Map<String, Integer> getMonthlyAdmissions(int year) throws Exception {
        Map<String, Integer> result = new LinkedHashMap<>();
        String sql = """
                    SELECT strftime('%m', admission_date, 'utc') AS month, COUNT(*) AS count
                    FROM animals
                    WHERE strftime('%Y', admission_date, 'utc') = ?
                    GROUP BY month ORDER BY month
                """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, String.valueOf(year));
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String month = rs.getString("month");
                int count = rs.getInt("count");
                result.put(month, count);
            }
        } catch (SQLException e) {
            throw new Exception("Error fetching monthly admissions", e);
        }
        return result;
    }

    @Override
    public Map<String, Integer> getAnimalOrigins(int year) throws Exception {
        Map<String, Integer> result = new LinkedHashMap<>();
        String sql = """
                SELECT p.name AS place_name, pr.name AS province_name, COUNT(*) AS count
                FROM animals a
                JOIN places p ON a.place_id = p.id
                JOIN provinces pr ON p.province_id = pr.id
                WHERE strftime('%Y', a.admission_date, 'utc') = ?
                GROUP BY p.name, pr.name 
                ORDER BY count DESC
            """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, String.valueOf(year));
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String placeName = rs.getString("place_name");
                String provinceName = rs.getString("province_name");
                int count = rs.getInt("count");

                String origin = placeName + ", " + provinceName;
                result.put(origin, count);
            }
        } catch (SQLException e) {
            throw new Exception("Error fetching animal origins", e);
        }
        return result;
    }

    @Override
    public int getTotalAdmissions(int year) throws Exception {

        String sql = """
                    SELECT COUNT(*) AS total
                    FROM animals
                    WHERE strftime('%Y', admission_date, 'utc') = ?
                """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, String.valueOf(year));
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("total");
            }
        } catch (SQLException e) {
            throw new Exception("Error fetching total admissions", e);
        }

        return 0; //Means there were NO admissions
    }

    @Override
    public double getAdoptionRate(int year) throws Exception {
        String totalSql = "SELECT COUNT(*) AS total FROM animals WHERE strftime('%Y', admission_date, 'utc') = ?";
        String adoptedSql = "SELECT COUNT(*) AS adopted FROM animals WHERE adopted = 1 AND strftime('%Y', admission_date, 'utc') = ?";

        try (
                PreparedStatement totalStmt = conn.prepareStatement(totalSql);
                PreparedStatement adoptedStmt = conn.prepareStatement(adoptedSql)
        ) {
            String yearStr = String.valueOf(year);

            totalStmt.setString(1, yearStr);
            adoptedStmt.setString(1, yearStr);

            ResultSet totalRs = totalStmt.executeQuery();
            ResultSet adoptedRs = adoptedStmt.executeQuery();

            int total = totalRs.next() ? totalRs.getInt("total") : 0;
            int adopted = adoptedRs.next() ? adoptedRs.getInt("adopted") : 0;

            if (total == 0) return 0.0;
            return (adopted / (double) total) * 100;
        } catch (SQLException e) {
            throw new Exception("Error calculating adoption rate for year " + year, e);
        }
    }

}
