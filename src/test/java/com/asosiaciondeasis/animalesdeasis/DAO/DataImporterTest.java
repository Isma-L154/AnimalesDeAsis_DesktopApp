package com.asosiaciondeasis.animalesdeasis.DAO;

import com.asosiaciondeasis.animalesdeasis.TestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataImporterTest {

    private TestSupport.TestDatabase db;
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        db = TestSupport.newDatabase();
        conn = db.connection();
    }

    @AfterEach
    void tearDown() throws Exception {
        db.close();
    }

    @Test
    void parsesTheDataArrayOfAnApiResponse() {
        String json = """
                {"data": [{"idProvincia": 1, "descripcion": "San José"},
                          {"idProvincia": 2, "descripcion": "Alajuela"}]}
                """;

        assertEquals(2, DataImporter.parseData(json).length());
        assertEquals("Alajuela", DataImporter.parseData(json).getJSONObject(1).getString("descripcion"));
    }

    @Test
    void storesEveryProvinceWithItsCantons() throws Exception {
        DataImporter.store(conn, List.of(
                new DataImporter.Province(1, "San José", List.of("Central", "Escazú")),
                new DataImporter.Province(2, "Alajuela", List.of("Central"))));

        assertEquals(2, count("SELECT COUNT(*) FROM provinces"));
        assertEquals(3, count("SELECT COUNT(*) FROM places"));
    }

    /**
     * The regression. Provinces used to be written as they were fetched, so a
     * failure halfway left some behind, and the next start saw a non-empty table,
     * skipped the import, and never filled in the missing cantons.
     */
    @Test
    void aFailedImportLeavesNothingBehind() throws Exception {
        List<DataImporter.Province> provinces = List.of(
                new DataImporter.Province(1, "San José", List.of("Central")),
                new DataImporter.Province(2, null, List.of("Central")));

        assertThrows(Exception.class, () -> DataImporter.store(conn, provinces));

        assertEquals(0, count("SELECT COUNT(*) FROM provinces"));
        assertEquals(0, count("SELECT COUNT(*) FROM places"));
    }

    private int count(String sql) throws Exception {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
