package com.asosiaciondeasis.animalesdeasis.DAO;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.RowVersion;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/** Binds the {@code last_modified IS ? AND synced IS ?} guard used by sync writes. */
public final class RowVersionGuard {

    private RowVersionGuard() {
    }

    /**
     * Fills parameters {@code index} and {@code index + 1}. With no expected
     * version both are NULL, which a stored row never matches, so a row that
     * appeared since the read is left alone.
     */
    public static void bind(PreparedStatement pstmt, int index, RowVersion version) throws SQLException {
        if (version == null) {
            pstmt.setNull(index, Types.VARCHAR);
            pstmt.setNull(index + 1, Types.INTEGER);
            return;
        }
        pstmt.setString(index, version.lastModified());
        pstmt.setInt(index + 1, version.synced() ? 1 : 0);
    }
}
