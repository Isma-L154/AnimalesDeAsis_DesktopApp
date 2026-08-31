package com.asosiaciondeasis.animalesdeasis.Config;

import com.asosiaciondeasis.animalesdeasis.DAO.DataImporter;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

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

                // Enforce the schema's foreign keys (off by default in SQLite).
                DatabaseConnection.applyPragmas(conn);

                // Create tables + indexes (idempotent, shared with the test suite).
                createSchema(conn);

                try (Statement stmt = conn.createStatement()) {
                    /*
                     * Check if the province table is empty; if so, call the API to import the
                     * geographic data (provinces/places) of Costa Rica.
                     */
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS count FROM provinces");
                    if (rs.next() && rs.getInt("count") == 0) {
                        System.out.println("Provinces table empty, importing data from API...");
                        DataImporter.populateProvincesAndPlaces(conn);
                        System.out.println("✅ Data imported successfully.");
                    } else {
                        System.out.println("✅ Provinces table already populated.");
                    }
                }
                conn.close();

                System.out.println("✅ Tables created or verified successfully.");
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error initializing the database.");
        }
    }

    /**
     * Creates every table and index the application needs, if they do not already
     * exist. Extracted so both the production bootstrap and the test suite build an
     * identical schema from a single source of truth.
     *
     * @param conn an open connection (with {@code foreign_keys} already enabled)
     */
    public static void createSchema(Connection conn) throws java.sql.SQLException {
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
                    last_modified TEXT NOT NULL DEFAULT (datetime('now', 'utc')),
                    FOREIGN KEY (place_id) REFERENCES places(id) ON DELETE RESTRICT
                );
                """;

        String createVaccines = """
                CREATE TABLE IF NOT EXISTS vaccines (
                    id TEXT PRIMARY KEY,
                    animal_record_number TEXT NOT NULL,
                    vaccine_name TEXT NOT NULL,
                    vaccination_date TEXT,
                    synced INTEGER NOT NULL DEFAULT 0, -- 0 = Not synced, 1 = Synced
                    last_modified TEXT NOT NULL DEFAULT (datetime('now', 'utc')),
                    FOREIGN KEY (animal_record_number) REFERENCES animals(record_number) ON DELETE CASCADE
                );
                """;

        /*
         * Records a vaccine that was deleted here, so the deletion survives long
         * enough to reach Firebase.
         *
         * Vaccines are hard-deleted, unlike animals, which carry an `active` flag
         * that synchronises like any other change. So deleting a vaccine with no
         * connection left no trace at all, and the next pull found the row in
         * Firebase, saw nothing locally, and put it back. The record returned
         * days later with no explanation - the kind of fault people work around
         * rather than report, because it looks like they imagined it.
         *
         * A row lives here from the moment of deletion until the deletion has
         * been applied to Firebase, and is then removed.
         */
        String createDeletedVaccines = """
                CREATE TABLE IF NOT EXISTS deleted_vaccines (
                    id TEXT PRIMARY KEY,               -- the deleted vaccine's id
                    animal_record_number TEXT NOT NULL,
                    deleted_at TEXT NOT NULL DEFAULT (datetime('now', 'utc'))
                );
                """;

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createProvinces);
            stmt.execute(createPlaces);
            stmt.execute(createAnimals);
            stmt.execute(createVaccines);
            stmt.execute(createDeletedVaccines);

            // --- Indexes for the hot query paths (sync filters, listings, joins) ---
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_animals_synced ON animals(synced)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_animals_active ON animals(active)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_animals_admission_date ON animals(admission_date)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_animals_place_id ON animals(place_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_vaccines_animal ON vaccines(animal_record_number)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_vaccines_synced ON vaccines(synced)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_places_province ON places(province_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_deleted_vaccines_animal "
                    + "ON deleted_vaccines(animal_record_number)");
        }
    }
}
