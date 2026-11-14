package com.example.brainblocks.tools;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

/** Test access to DataCopy's package-private steps. */
public final class DataCopyAccess {
    private DataCopyAccess() {}

    public static Connection open(String url) throws Exception {
        Connection c = DriverManager.getConnection(url);
        c.setAutoCommit(false);
        return c;
    }

    public static int dump(Connection c, Path zip) throws Exception {
        return DataCopy.dump(c, zip);
    }

    public static int load(Path zip, Connection c, boolean replace) throws Exception {
        return DataCopy.load(zip, c, replace);
    }
}
