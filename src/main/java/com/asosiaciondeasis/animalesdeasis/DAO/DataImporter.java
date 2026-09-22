package com.asosiaciondeasis.animalesdeasis.DAO;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads Costa Rica's provinces and cantons into the {@code provinces} and
 * {@code places} tables. Runs once, on the first start: the application is
 * offline-first and never needs the API again.
 */
public final class DataImporter {

    private static final String BASE_API = "https://api-geo-cr.vercel.app";

    /**
     * Bounds every request. Without it a stalled API held the splash screen
     * forever, with nothing on screen to say why.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    record Province(int id, String name, List<String> cantons) {
    }

    private DataImporter() {
    }

    /**
     * Fetches everything first, then writes it in one transaction.
     *
     * <p>Writing as it fetched meant a failure halfway left some provinces
     * behind. The next start then saw a non-empty table, skipped the import, and
     * the missing cantons never arrived - animals from those places could not be
     * registered at all.</p>
     */
    public static void populateProvincesAndPlaces(Connection conn) throws Exception {
        store(conn, fetchProvinces());
    }

    private static List<Province> fetchProvinces() throws IOException, InterruptedException {
        JSONArray provinces = parseData(get(BASE_API + "/provincias?limit=100&page=1"));
        List<Province> result = new ArrayList<>();
        for (int i = 0; i < provinces.length(); i++) {
            JSONObject province = provinces.getJSONObject(i);
            int id = province.getInt("idProvincia");

            JSONArray cantons = parseData(get(BASE_API + "/provincias/" + id + "/cantones?limit=100&page=1"));
            List<String> cantonNames = new ArrayList<>();
            for (int j = 0; j < cantons.length(); j++) {
                cantonNames.add(cantons.getJSONObject(j).getString("descripcion"));
            }
            result.add(new Province(id, province.getString("descripcion"), cantonNames));
        }
        return result;
    }

    static void store(Connection conn, List<Province> provinces) throws SQLException {
        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (PreparedStatement insertProvince = conn.prepareStatement(
                     "INSERT OR IGNORE INTO provinces (id, name) VALUES (?, ?)");
             PreparedStatement insertPlace = conn.prepareStatement(
                     "INSERT OR IGNORE INTO places (name, province_id) VALUES (?, ?)")) {
            for (Province province : provinces) {
                insertProvince.setInt(1, province.id());
                insertProvince.setString(2, province.name());
                insertProvince.executeUpdate();

                for (String canton : province.cantons()) {
                    insertPlace.setString(1, canton);
                    insertPlace.setInt(2, province.id());
                    insertPlace.addBatch();
                }
            }
            insertPlace.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(previousAutoCommit);
        }
    }

    private static String get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("GET " + url + " returned HTTP " + response.statusCode());
        }
        return response.body();
    }

    /** The API wraps every list in {@code {"data": [...]}}. */
    static JSONArray parseData(String json) {
        return new JSONObject(json).getJSONArray("data");
    }
}
