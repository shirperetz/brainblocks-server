package com.example.brainblocks.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 2026-11-14 (Shir: "move all my data", "a nightly backup"): copies the BrainBlocks database between
 * databases and backup files - MySQL on this PC -> PostgreSQL in the cloud (Neon) once, and every night
 * the cloud -> a backup file on this PC (and back, if ever needed). Plain JDBC, no Spring: the app itself
 * creates the tables (it starts once on the empty database first).
 *
 * <pre>
 *   copy  --from &lt;jdbc url&gt; --to &lt;jdbc url&gt; [--replace]   database -> database
 *   dump  --from &lt;jdbc url&gt; --to &lt;file.zip&gt;                database -> backup file
 *   load  --from &lt;file.zip&gt; --to &lt;jdbc url&gt; [--replace]   backup file -> database
 *   check --from &lt;jdbc url or file.zip&gt;                    counts + fingerprint of every table
 * </pre>
 * Users / passwords come from environment variables (FROM_USER, FROM_PASSWORD, TO_USER, TO_PASSWORD),
 * never from the command line. Only tables that exist on both sides are copied, only their shared
 * columns (old tables of the deleted Math Race stay behind). Every copy ends by comparing row counts and
 * a fingerprint of every value of every table - any difference = exit code 2.
 * Run: java -cp brainblocks.jar -Dloader.main=com.example.brainblocks.tools.DataCopy
 *      org.springframework.boot.loader.launch.PropertiesLauncher copy --from ... --to ...
 */
public final class DataCopy {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int BATCH = 500;

    private DataCopy() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1) usage();
        Map<String, String> o = options(args);
        String cmd = args[0];
        int code = switch (cmd) {
            case "copy" -> copy(open(o.get("from"), "FROM"), open(o.get("to"), "TO"), o.containsKey("replace"));
            case "dump" -> dump(open(o.get("from"), "FROM"), Path.of(o.get("to")));
            case "load" -> load(Path.of(o.get("from")), open(o.get("to"), "TO"), o.containsKey("replace"));
            case "check" -> {
                Map<String, String> f = o.get("from").endsWith(".zip") ? manifest(Path.of(o.get("from"))) : fingerprints(open(o.get("from"), "FROM"));
                f.forEach((t, v) -> System.out.println(t + "  " + v));
                yield 0;
            }
            default -> {
                usage();
                yield 1;
            }
        };
        System.exit(code);
    }

    private static void usage() {
        System.err.println("usage: copy|dump|load|check --from <jdbc|zip> [--to <jdbc|zip>] [--replace]");
        System.exit(1);
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> o = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--")) continue;
            String k = a.substring(2);
            if (k.equals("replace")) o.put(k, "yes");
            else if (i + 1 < args.length) o.put(k, args[++i]);
        }
        if (!o.containsKey("from")) usage();
        return o;
    }

    static Connection open(String url, String who) throws Exception {
        if (url == null || !url.startsWith("jdbc:")) throw new IllegalArgumentException(who + ": not a jdbc url");
        String user = System.getenv(who + "_USER");
        String pass = System.getenv(who + "_PASSWORD");
        Connection c = user == null ? DriverManager.getConnection(url) : DriverManager.getConnection(url, user, pass == null ? "" : pass);
        c.setAutoCommit(false);
        return c;
    }

    // ---- what is there ---------------------------------------------------------------------------

    enum Kind { MYSQL, POSTGRES, H2, OTHER }

    static Kind kind(Connection c) throws Exception {
        String p = c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        return p.contains("mysql") || p.contains("mariadb") ? Kind.MYSQL : p.contains("postgres") ? Kind.POSTGRES
                : p.contains("h2") ? Kind.H2 : Kind.OTHER;
    }

    /** The app's tables (lower case), each with its columns in order and their JDBC types. */
    static Map<String, Map<String, Integer>> tables(Connection c) throws Exception {
        DatabaseMetaData md = c.getMetaData();
        Kind k = kind(c);
        String catalog = k == Kind.MYSQL ? c.getCatalog() : null;
        String schema = k == Kind.POSTGRES ? "public" : k == Kind.H2 ? "public" : null;
        Map<String, Map<String, Integer>> out = new TreeMap<>();
        try (ResultSet rs = md.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String t = rs.getString("TABLE_NAME");
                out.put(t.toLowerCase(Locale.ROOT), new LinkedHashMap<>());
            }
        }
        for (String t : out.keySet()) {
            try (ResultSet rs = md.getColumns(catalog, schema, k == Kind.H2 ? t.toLowerCase(Locale.ROOT) : t, "%")) {
                while (rs.next()) out.get(t).put(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT), rs.getInt("DATA_TYPE"));
            }
        }
        return out;
    }

    static List<String> primaryKey(Connection c, String table) throws Exception {
        Kind k = kind(c);
        List<String> pk = new ArrayList<>();
        try (ResultSet rs = c.getMetaData().getPrimaryKeys(k == Kind.MYSQL ? c.getCatalog() : null,
                k == Kind.MYSQL ? null : "public", table)) {
            TreeMap<Integer, String> byPos = new TreeMap<>();
            while (rs.next()) byPos.put(rs.getInt("KEY_SEQ"), rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            pk.addAll(byPos.values());
        }
        return pk;
    }

    static String q(Connection c, String name) throws Exception {
        return kind(c) == Kind.MYSQL ? "`" + name + "`" : "\"" + name + "\"";
    }

    static String select(Connection c, String table, List<String> cols) throws Exception {
        StringBuilder b = new StringBuilder("SELECT ");
        for (int i = 0; i < cols.size(); i++) b.append(i == 0 ? "" : ", ").append(q(c, cols.get(i)));
        b.append(" FROM ").append(q(c, table));
        List<String> pk = primaryKey(c, table);
        List<String> order = pk.isEmpty() ? cols : pk;
        b.append(" ORDER BY ");
        for (int i = 0; i < order.size(); i++) b.append(i == 0 ? "" : ", ").append(q(c, order.get(i)));
        return b.toString();
    }

    // ---- values ----------------------------------------------------------------------------------

    /** One value as the same text on every database (for the fingerprint). */
    static String normal(Object v) throws Exception {
        if (v == null) return "\u0000";
        if (v instanceof Boolean b) return b ? "true" : "false";
        if (v instanceof byte[] bytes) return Base64.getEncoder().encodeToString(bytes);
        if (v instanceof java.sql.Timestamp ts) return normal(ts.toLocalDateTime());
        if (v instanceof java.sql.Date d) return d.toLocalDate().toString();
        if (v instanceof LocalDateTime t) return t.withNano(t.getNano() / 1000 * 1000).toString();
        if (v instanceof OffsetDateTime t) return normal(t.toLocalDateTime());
        if (v instanceof Number n) {
            BigDecimal d = n instanceof BigDecimal bd ? bd : new BigDecimal(n.toString());
            return d.compareTo(BigDecimal.ZERO) == 0 ? "0" : d.stripTrailingZeros().toPlainString();
        }
        if (v instanceof java.sql.Clob clob) return clob.getSubString(1, (int) clob.length());
        return v.toString();
    }

    static Object read(ResultSet rs, int i, int type) throws Exception {
        Object v;
        switch (type) {
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> v = rs.getObject(i, LocalDateTime.class);
            case Types.DATE -> v = rs.getObject(i, LocalDate.class);
            case Types.TIME -> v = rs.getObject(i, LocalTime.class);
            case Types.BIT, Types.BOOLEAN -> {
                boolean b = rs.getBoolean(i);
                v = rs.wasNull() ? null : b;
            }
            default -> v = rs.getObject(i);
        }
        return v;
    }

    /** A value made fit for the target column's type. */
    static Object fit(Object v, int type) {
        if (v == null) return null;
        switch (type) {
            case Types.BIT, Types.BOOLEAN:
                if (v instanceof Boolean) return v;
                if (v instanceof Number n) return n.intValue() != 0;
                return Boolean.parseBoolean(v.toString()) || "1".equals(v.toString());
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER:
                if (v instanceof Boolean b) return b ? 1 : 0;
                return v instanceof Number n ? (Object) n.intValue() : Integer.parseInt(v.toString());
            case Types.BIGINT:
                return v instanceof Number n ? (Object) n.longValue() : Long.parseLong(v.toString());
            case Types.REAL, Types.FLOAT, Types.DOUBLE:
                return v instanceof Number n ? (Object) n.doubleValue() : Double.parseDouble(v.toString());
            case Types.NUMERIC, Types.DECIMAL:
                return v instanceof BigDecimal ? v : new BigDecimal(v.toString());
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE:
                return v instanceof LocalDateTime ? v : LocalDateTime.parse(v.toString());
            case Types.DATE:
                return v instanceof LocalDate ? v : LocalDate.parse(v.toString());
            case Types.TIME:
                return v instanceof LocalTime ? v : LocalTime.parse(v.toString());
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB:
                return v instanceof byte[] ? v : Base64.getDecoder().decode(v.toString());
            default:
                return v instanceof byte[] b ? new String(b, StandardCharsets.UTF_8) : v.toString();
        }
    }

    // ---- writing a table ---------------------------------------------------------------------------

    private interface Rows {
        /** Next row (values in [cols] order), or null at the end. */
        Object[] next() throws Exception;
    }

    private static int write(Connection to, String table, List<String> cols, Map<String, Integer> types, Rows rows) throws Exception {
        StringBuilder b = new StringBuilder("INSERT INTO ").append(q(to, table)).append(" (");
        for (int i = 0; i < cols.size(); i++) b.append(i == 0 ? "" : ", ").append(q(to, cols.get(i)));
        b.append(") VALUES (").append("?, ".repeat(cols.size() - 1)).append("?)");
        int n = 0;
        try (PreparedStatement ps = to.prepareStatement(b.toString())) {
            Object[] row;
            while ((row = rows.next()) != null) {
                for (int i = 0; i < cols.size(); i++) {
                    int type = types.get(cols.get(i));
                    Object v = fit(row[i], type);
                    if (v == null) ps.setNull(i + 1, type);
                    else ps.setObject(i + 1, v);
                }
                ps.addBatch();
                if (++n % BATCH == 0) ps.executeBatch();
            }
            ps.executeBatch();
        }
        return n;
    }

    /** Empties the target's tables (only with --replace); refuses a target that already has data. */
    private static void prepare(Connection to, Iterable<String> tables, boolean replace) throws Exception {
        for (String t : tables) {
            long rows;
            try (Statement s = to.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + q(to, t))) {
                rs.next();
                rows = rs.getLong(1);
            }
            if (rows > 0 && !replace) {
                throw new IllegalStateException("the target already has data in " + t + " (" + rows + " rows) - nothing was changed. "
                        + "Run again with --replace to replace it.");
            }
        }
        if (replace) {
            for (String t : tables) {
                try (Statement s = to.createStatement()) {
                    s.executeUpdate("DELETE FROM " + q(to, t));
                }
            }
        }
    }

    /** After inserting ids by hand, the next new row must get a free id. */
    private static void fixIds(Connection to, Map<String, Map<String, Integer>> target, Iterable<String> tables) throws Exception {
        Kind k = kind(to);
        for (String t : tables) {
            if (!target.get(t).containsKey("id")) continue;
            long max;
            try (Statement s = to.createStatement(); ResultSet rs = s.executeQuery("SELECT COALESCE(MAX(id), 0) FROM " + q(to, t))) {
                rs.next();
                max = rs.getLong(1);
            }
            try (Statement s = to.createStatement()) {
                if (k == Kind.POSTGRES) {
                    try (ResultSet rs = s.executeQuery("SELECT pg_get_serial_sequence('" + t + "', 'id')")) {
                        rs.next();
                        String seq = rs.getString(1);
                        if (seq != null) {
                            try (Statement s2 = to.createStatement()) {
                                s2.execute("SELECT setval('" + seq + "', " + Math.max(1, max) + ", " + (max > 0) + ")");
                            }
                        }
                    }
                } else if (k == Kind.H2) {
                    s.execute("ALTER TABLE " + q(to, t) + " ALTER COLUMN id RESTART WITH " + (max + 1));
                } else if (k == Kind.MYSQL) {
                    s.execute("ALTER TABLE " + q(to, t) + " AUTO_INCREMENT = " + (max + 1));
                }
            }
        }
    }

    // ---- the commands ------------------------------------------------------------------------------

    static int copy(Connection from, Connection to, boolean replace) throws Exception {
        Map<String, Map<String, Integer>> src = tables(from);
        Map<String, Map<String, Integer>> dst = tables(to);
        List<String> shared = new ArrayList<>(dst.keySet());
        shared.retainAll(src.keySet());
        List<String> skipped = new ArrayList<>(src.keySet());
        skipped.removeAll(dst.keySet());
        if (!skipped.isEmpty()) System.out.println("not copied (no such table on the target): " + skipped);
        prepare(to, shared, replace);
        Map<String, List<String>> colsOf = new LinkedHashMap<>();
        for (String t : shared) {
            List<String> cols = new ArrayList<>(dst.get(t).keySet());
            cols.retainAll(src.get(t).keySet());
            List<String> onlySource = new ArrayList<>(src.get(t).keySet());
            onlySource.removeAll(dst.get(t).keySet());
            if (!onlySource.isEmpty()) System.out.println(t + ": old columns not copied " + onlySource);
            colsOf.put(t, cols);
            Map<String, Integer> srcTypes = src.get(t);
            try (Statement s = from.createStatement(); ResultSet rs = s.executeQuery(select(from, t, cols))) {
                int n = write(to, t, cols, dst.get(t), () -> {
                    if (!rs.next()) return null;
                    Object[] row = new Object[cols.size()];
                    for (int i = 0; i < cols.size(); i++) row[i] = read(rs, i + 1, srcTypes.get(cols.get(i)));
                    return row;
                });
                System.out.println("copied " + t + ": " + n);
            }
        }
        fixIds(to, dst, shared);
        to.commit();
        return compare(fingerprints(from, colsOf), fingerprints(to, colsOf));
    }

    static int dump(Connection from, Path zip) throws Exception {
        Map<String, Map<String, Integer>> src = tables(from);
        Map<String, List<String>> colsOf = new LinkedHashMap<>();
        Path tmp = zip.resolveSibling(zip.getFileName() + ".part");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(tmp))) {
            for (var e : src.entrySet()) {
                String t = e.getKey();
                List<String> cols = new ArrayList<>(e.getValue().keySet());
                colsOf.put(t, cols);
                z.putNextEntry(new ZipEntry(t + ".jsonl"));
                BufferedWriter w = new BufferedWriter(new OutputStreamWriter(z, StandardCharsets.UTF_8));
                ObjectNode head = JSON.createObjectNode();
                ArrayNode cn = head.putArray("columns");
                cols.forEach(cn::add);
                w.write(JSON.writeValueAsString(head));
                w.newLine();
                try (Statement s = from.createStatement(); ResultSet rs = s.executeQuery(select(from, t, cols))) {
                    while (rs.next()) {
                        ArrayNode row = JSON.createArrayNode();
                        for (int i = 0; i < cols.size(); i++) {
                            Object v = read(rs, i + 1, e.getValue().get(cols.get(i)));
                            if (v == null) row.addNull();
                            else if (v instanceof Boolean b) row.add(b);
                            else if (v instanceof Integer n) row.add(n);
                            else if (v instanceof Long n) row.add(n);
                            else if (v instanceof Short n) row.add(n.intValue());
                            else if (v instanceof Byte n) row.add(n.intValue());
                            else if (v instanceof Double n) row.add(n);
                            else if (v instanceof Float n) row.add(n.doubleValue());
                            else if (v instanceof BigDecimal n) row.add(n);
                            else if (v instanceof byte[] bytes) row.add(Base64.getEncoder().encodeToString(bytes));
                            else row.add(v.toString());
                        }
                        w.write(JSON.writeValueAsString(row));
                        w.newLine();
                    }
                }
                w.flush();
                z.closeEntry();
            }
            Map<String, String> f = fingerprints(from, colsOf);
            z.putNextEntry(new ZipEntry("manifest.json"));
            ObjectNode m = JSON.createObjectNode();
            m.put("made", LocalDateTime.now().toString());
            m.put("database", from.getMetaData().getDatabaseProductName());
            ObjectNode tn = m.putObject("tables");
            f.forEach(tn::put);
            z.write(JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(m));
            z.closeEntry();
        }
        from.rollback();
        Files.move(tmp, zip, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        // read it back: the file must hold exactly what the database held
        int bad = compare(fingerprints(from, colsOf), fingerprintsOf(zip, from, colsOf, src));
        System.out.println("backup " + zip + " (" + Files.size(zip) / 1024 + " KB) " + (bad == 0 ? "verified" : "DIFFERENT"));
        return bad;
    }

    /** The fingerprints the zip itself gives, decoded with the source's column types. */
    private static Map<String, String> fingerprintsOf(Path zip, Connection like, Map<String, List<String>> colsOf,
                                                      Map<String, Map<String, Integer>> types) throws Exception {
        Map<String, String> out = new TreeMap<>();
        try (ZipFile z = new ZipFile(zip.toFile())) {
            for (var e : colsOf.entrySet()) {
                String t = e.getKey();
                List<String> cols = e.getValue();
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                long n = 0;
                try (BufferedReader r = new BufferedReader(new InputStreamReader(z.getInputStream(z.getEntry(t + ".jsonl")), StandardCharsets.UTF_8))) {
                    List<String> fileCols = columnsOf(r.readLine());
                    String line;
                    while ((line = r.readLine()) != null) {
                        JsonNode row = JSON.readTree(line);
                        StringBuilder b = new StringBuilder();
                        for (String c : cols) {
                            JsonNode v = row.get(fileCols.indexOf(c));
                            b.append(normal(fit(jsonValue(v), types.get(t).get(c)))).append('\u0001');
                        }
                        md.update(b.append('\n').toString().getBytes(StandardCharsets.UTF_8));
                        n++;
                    }
                }
                out.put(t, n + " rows " + HexFormat.of().formatHex(md.digest()).substring(0, 16));
            }
        }
        return out;
    }

    private static List<String> columnsOf(String headLine) throws Exception {
        List<String> cols = new ArrayList<>();
        JSON.readTree(headLine).get("columns").forEach(c -> cols.add(c.asText()));
        return cols;
    }

    private static Object jsonValue(JsonNode v) {
        if (v == null || v.isNull()) return null;
        if (v.isBoolean()) return v.booleanValue();
        if (v.isIntegralNumber()) return v.longValue();
        if (v.isNumber()) return v.decimalValue();
        return v.asText();
    }

    static int load(Path zip, Connection to, boolean replace) throws Exception {
        Map<String, Map<String, Integer>> dst = tables(to);
        Map<String, List<String>> colsOf = new LinkedHashMap<>();
        try (ZipFile z = new ZipFile(zip.toFile())) {
            List<String> shared = new ArrayList<>();
            for (String t : dst.keySet()) if (z.getEntry(t + ".jsonl") != null) shared.add(t);
            prepare(to, shared, replace);
            for (String t : shared) {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(z.getInputStream(z.getEntry(t + ".jsonl")), StandardCharsets.UTF_8))) {
                    List<String> fileCols = columnsOf(r.readLine());
                    List<String> cols = new ArrayList<>(dst.get(t).keySet());
                    cols.retainAll(fileCols);
                    colsOf.put(t, cols);
                    int n = write(to, t, cols, dst.get(t), () -> {
                        String line = r.readLine();
                        if (line == null) return null;
                        JsonNode row = JSON.readTree(line);
                        Object[] out = new Object[cols.size()];
                        for (int i = 0; i < cols.size(); i++) out[i] = jsonValue(row.get(fileCols.indexOf(cols.get(i))));
                        return out;
                    });
                    System.out.println("loaded " + t + ": " + n);
                }
            }
            fixIds(to, dst, shared);
            to.commit();
            return compare(fingerprintsOf(zip, to, colsOf, dst), fingerprints(to, colsOf));
        }
    }

    // ---- fingerprints ------------------------------------------------------------------------------

    static Map<String, String> fingerprints(Connection c) throws Exception {
        Map<String, List<String>> colsOf = new LinkedHashMap<>();
        tables(c).forEach((t, cols) -> colsOf.put(t, new ArrayList<>(cols.keySet())));
        return fingerprints(c, colsOf);
    }

    /** Row count + SHA-256 of every value (in key order) of every table. */
    static Map<String, String> fingerprints(Connection c, Map<String, List<String>> colsOf) throws Exception {
        Map<String, Map<String, Integer>> types = tables(c);
        Map<String, String> out = new TreeMap<>();
        for (var e : colsOf.entrySet()) {
            String t = e.getKey();
            List<String> cols = e.getValue();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            long n = 0;
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(select(c, t, cols))) {
                while (rs.next()) {
                    StringBuilder b = new StringBuilder();
                    for (int i = 0; i < cols.size(); i++) {
                        int type = types.get(t).get(cols.get(i));
                        b.append(normal(fit(read(rs, i + 1, type), type))).append('\u0001');
                    }
                    md.update(b.append('\n').toString().getBytes(StandardCharsets.UTF_8));
                    n++;
                }
            }
            out.put(t, n + " rows " + HexFormat.of().formatHex(md.digest()).substring(0, 16));
        }
        c.rollback();
        return out;
    }

    private static Map<String, String> manifest(Path zip) throws Exception {
        try (ZipFile z = new ZipFile(zip.toFile())) {
            JsonNode m = JSON.readTree(z.getInputStream(z.getEntry("manifest.json")));
            Map<String, String> out = new TreeMap<>();
            m.get("tables").fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asText()));
            return out;
        }
    }

    private static int compare(Map<String, String> a, Map<String, String> b) {
        int bad = 0;
        for (String t : a.keySet()) {
            boolean same = a.get(t).equals(b.get(t));
            if (!same) bad++;
            System.out.println((same ? "  same  " : "  DIFF  ") + t + "  " + a.get(t) + (same ? "" : "  vs  " + b.get(t)));
        }
        System.out.println(bad == 0 ? "ALL TABLES IDENTICAL" : bad + " TABLE(S) DIFFERENT");
        return bad == 0 ? 0 : 2;
    }

    /** For the test: the same normalization the fingerprint uses. */
    static String normalForTest(Object v) throws Exception {
        return normal(v);
    }

    static Object readValue(ResultSetMetaData md, ResultSet rs, int i) throws Exception {
        return read(rs, i, md.getColumnType(i));
    }
}
