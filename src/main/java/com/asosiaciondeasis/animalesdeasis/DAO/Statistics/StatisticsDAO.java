package com.asosiaciondeasis.animalesdeasis.DAO.Statistics;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Statistics.IStatisticsDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Yearly figures for the statistics dashboard and the CSV export.
 *
 * <p>{@code admission_date} is already stored as a UTC timestamp, so years are
 * read from it as-is. Passing it through SQLite's {@code 'utc'} modifier treated
 * it as local time and shifted it again: on a machine east of Greenwich a
 * 1 January admission was counted in the previous year.</p>
 */
public class StatisticsDAO implements IStatisticsDAO {

    private final Connection conn;

    public StatisticsDAO(Connection conn) {
        this.conn = conn;
    }

    @Override
    public Map<String, Integer> getMonthlyAdmissions(int year) throws Exception {
        String sql = """
                SELECT strftime('%m', admission_date) AS month, COUNT(*) AS count
                FROM animals
                WHERE strftime('%Y', admission_date) = ?
                GROUP BY month ORDER BY month
                """;
        Map<String, Integer> result = new LinkedHashMap<>();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, String.valueOf(year));
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("month"), rs.getInt("count"));
                }
            }
        } catch (SQLException e) {
            throw new Exception("Error fetching monthly admissions", e);
        }
        return result;
    }

    @Override
    public Map<String, Integer> getAnimalOrigins(int year) throws Exception {
        String sql = """
                SELECT p.name AS place_name, pr.name AS province_name, COUNT(*) AS count
                FROM animals a
                JOIN places p ON a.place_id = p.id
                JOIN provinces pr ON p.province_id = pr.id
                WHERE strftime('%Y', a.admission_date) = ?
                GROUP BY p.name, pr.name
                ORDER BY count DESC
                """;
        Map<String, Integer> result = new LinkedHashMap<>();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, String.valueOf(year));
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("place_name") + ", " + rs.getString("province_name"),
                            rs.getInt("count"));
                }
            }
        } catch (SQLException e) {
            throw new Exception("Error fetching animal origins", e);
        }
        return result;
    }

    @Override
    public int getTotalAdmissions(int year) throws Exception {
        String sql = "SELECT COUNT(*) FROM animals WHERE strftime('%Y', admission_date) = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, String.valueOf(year));
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new Exception("Error fetching total admissions", e);
        }
    }

    /** Adopted animals as a percentage of those admitted in {@code year}; 0 when none were. */
    @Override
    public double getAdoptionRate(int year) throws Exception {
        String sql = """
                SELECT COUNT(*) AS total, COALESCE(SUM(adopted), 0) AS adopted
                FROM animals
                WHERE strftime('%Y', admission_date) = ?
                """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, String.valueOf(year));
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next() || rs.getInt("total") == 0) {
                    return 0.0;
                }
                return rs.getInt("adopted") * 100.0 / rs.getInt("total");
            }
        } catch (SQLException e) {
            throw new Exception("Error calculating adoption rate for year " + year, e);
        }
    }
}
