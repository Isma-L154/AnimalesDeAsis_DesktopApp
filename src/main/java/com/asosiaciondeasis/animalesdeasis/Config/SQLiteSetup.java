package com.asosiaciondeasis.animalesdeasis.Config;

import java.io.File;
import java.sql.*;

import com.asosiaciondeasis.animalesdeasis.DAO.DataImporter;

public class SQLiteSetup {

    /**
     * Initializes the SQLite database by creating the necessary folder, database file,
     * and tables if they do not already exist.
     */

    public static void initializeDatabase() {
    try {

        // Get the user's home directory path
        String userHome = System.getProperty("user.home");

        // Define the hidden folder path inside the user's home directory
        File dir = new File(userHome, ".asociaciondeasis");

        // Create the directory if it doesn't exist
        if (!dir.exists()) {
            dir.mkdirs();
            System.out.println("Directory created: " + dir.getAbsolutePath());
        }

        // Define the database file inside the directory
        File dbFile = new File(dir, "AsociacionDeAsis.db");

        // Create the JDBC URL pointing to the SQLite database file
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        // Establish a connection to the SQLite database (creates the file if it doesn't exist)
        Connection conn = DriverManager.getConnection(url);

        if (conn != null) {
            System.out.println("✅ Database connected at: " + dbFile.getAbsolutePath());

            Statement stmt = conn.createStatement();

            // --- Create tables with constraints ---

            String createProvinces = """
                    CREATE TABLE IF NOT EXISTS provinces (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE
                    );
                    """;

            String createPlaces = """
                    CREATE TABLE IF NOT EXISTS places (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        province_id INTEGER NOT NULL,
                        FOREIGN KEY (province_id) REFERENCES provinces(id) ON DELETE CASCADE
                    );
                    """;

            String createAnimals = """
                    CREATE TABLE IF NOT EXISTS animals (
                        record_number TEXT PRIMARY KEY, -- UUID
                        chip_number TEXT UNIQUE,
                        barcode TEXT UNIQUE,
                        admission_date TEXT NOT NULL, -- Format: DD-MM-YYYY
                        collected_by TEXT,
                        place_id INTEGER NOT NULL,
                        reason_for_rescue TEXT,
                        species TEXT NOT NULL CHECK (species IN ('Perro', 'Gato')),
                        approximate_age INTEGER,
                        sex TEXT CHECK (sex IN ('Macho', 'Hembra')),
                        name TEXT,
                        ailments TEXT,
                        neutering_date TEXT,
                        adopted INTEGER NOT NULL DEFAULT 0, -- 0 = Not adopted, 1 = Adopted
                        active INTEGER NOT NULL DEFAULT 1, -- 1 = Active, 0 = Deleted (soft delete)
                        synced INTEGER NOT NULL DEFAULT 0, -- 0 = Not synced, 1 = Synced
                        FOREIGN KEY (place_id) REFERENCES places(id) ON DELETE SET NULL
                    );
                    """;

            String createVaccines = """
                    CREATE TABLE IF NOT EXISTS vaccines (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        animal_record_number TEXT NOT NULL,
                        vaccine_name TEXT NOT NULL,
                        vaccination_date TEXT,
                        synced INTEGER NOT NULL DEFAULT 0, -- 0 = Not synced, 1 = Synced
                        FOREIGN KEY (animal_record_number) REFERENCES animals(record_number) ON DELETE CASCADE
                    );
                    """;

            // Execute all statements that creates the tables if they don't exist
            stmt.execute(createProvinces);
            stmt.execute(createPlaces);
            stmt.execute(createAnimals);
            stmt.execute(createVaccines);

            /**
             * Check if provinces table is empty, if not, we call the API to import the info of the GEO of CR
             * */
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS count FROM provinces");
            if (rs.next() && rs.getInt("count") == 0) {
                System.out.println("Provinces table empty, importing data from API...");
                DataImporter.populateProvincesAndPlaces(conn); //Call the method we have on DAO
                System.out.println("✅ Data imported successfully.");
            } else {
                System.out.println("Provinces table already populated.");
            }
            stmt.close();
            conn.close();

            System.out.println("✅ Tables created or verified successfully.");
        }
    }catch(Exception e){
        e.printStackTrace();
        throw new RuntimeException("Error initializing the database.");
    }
    }
}
