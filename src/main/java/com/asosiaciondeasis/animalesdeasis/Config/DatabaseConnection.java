package com.asosiaciondeasis.animalesdeasis.Config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    private static final String DB_PATH = System.getProperty("user.home") + "/.asociaciondeasis/AsociacionDeAsis.db";
    private static final String DB_URL = "jdbc:sqlite:" + DB_PATH;

    private DatabaseConnection() {
        // Private constructor to prevent instantiation
    }

    /**
     * Mehtod to get autom connection to the DB
     * */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

}
