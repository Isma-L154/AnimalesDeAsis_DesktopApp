package com.asosiaciondeasis.animalesdeasis.DAO;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.sql.*;


public class DataImporter {

    //Base URL for the API of Costa Rica Geo
    private static final String BASE_API = "https://api-geo-cr.vercel.app";

    /**
     * Fetches provinces and their corresponding cantons from the API,
     * and populates the 'provinces' and 'places' tables in the local SQLite database.
     *
     * This method is intended to be run only once during initial setup, because the whole desktop app
     * is intended to be used offline.
     */

    public static void populateProvincesAndPlaces(Connection conn) {

        try{
            /**
             *  Fetch all provinces from the API
             * URL -> https://api-geo-cr.vercel.app/provincias?limit=100&page=1
             * */
            JSONArray provinces = fetchJsonObject(BASE_API + "/provincias?limit=100&page=1");


            for (int i = 0; i < provinces.length(); i++) {
                JSONObject province = provinces.getJSONObject(i);
                int provinceId = province.getInt("idProvincia");
                String provinceName = province.getString("descripcion");

                // Insert province into the database (ignore if already exists)
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT OR IGNORE INTO provinces (id, name) VALUES (?, ?)")) {
                    ps.setInt(1, provinceId);
                    ps.setString(2, provinceName);
                    ps.executeUpdate();
                }


                /**
                 *  Fetch all the cantons from the API from the current province
                 * URL -> https://api-geo-cr.vercel.app/provincias/{id}/cantones
                 * */

                // Fetch cantons for the current province
                JSONArray cantones = fetchJsonObject(BASE_API + "/provincias/" + provinceId + "/cantones?limit=100&page=1");

                for (int j = 0; j < cantones.length(); j++) {
                    JSONObject canton = cantones.getJSONObject(j);
                    String cantonName = canton.getString("descripcion");

                    // Insert canton (place) into the database (linked to province)
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR IGNORE INTO places (name, province_id) VALUES (?, ?)")) {
                        ps.setString(1, cantonName);
                        ps.setInt(2, provinceId);
                        ps.executeUpdate();
                    }
                }
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }





    }

    /**
     * Makes a GET request to the given URL and returns the response as a JSONObject.
     *
     * @param urlStr API endpoint URL
     * @return JSONArray parsed from the JSON response
     * @throws Exception on connection or parsing failure
     */
    private static JSONArray fetchJsonObject(String urlStr) throws Exception {

        URL url = URI.create(urlStr).toURL();
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestProperty("Accept", "application/json");
        con.setRequestMethod("GET");


        /**
         * This line creates a reader that allows you to read the textual content sent by the server,
         * line by line, with the correct encoding (UTF-8).
         * */
        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream(), "UTF-8"));

        /**
         * Read the response content line by line
         * */
        StringBuilder content = new StringBuilder();
        String line;

        while ((line = in.readLine()) != null) {
            content.append(line);
        }

        in.close();
        con.disconnect();

        /**
         * Parse the JSON string into a JSONArray and return it
         * */
        JSONObject responseObject = new JSONObject(content.toString());
        return responseObject.getJSONArray("data");
    }
}
