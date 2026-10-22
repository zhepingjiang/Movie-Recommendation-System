// Not under com.movierec.backend on purpose: Flyway finds Java migrations by scanning its
// locations (spring.flyway.locations = classpath:db/migration), and this package is that path.
package db.migration;

import com.movierec.backend.geo.H3Cells;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Seeds every Ontario cinema from {@code db/seed/ontario_theaters.csv} (OpenStreetMap data, (c)
 * OpenStreetMap contributors, ODbL -- produced by backend/scripts/fetch_ontario_theaters.py).
 *
 * <p>A Java migration rather than SQL so {@code h3_cell} is computed by {@link H3Cells}, the same
 * code the Theater entity hook and the nearby search use; Postgres has no H3 functions without
 * the h3-pg extension. Values go in through a {@link PreparedStatement}, never string-built SQL.
 */
public class V16__seed_ontario_theaters extends BaseJavaMigration {

    private static final String CSV_RESOURCE = "db/seed/ontario_theaters.csv";
    private static final List<String> EXPECTED_HEADER =
            List.of("osm_id", "name", "brand", "address", "city", "postal_code", "lat", "lng");

    @Override
    public void migrate(Context context) throws Exception {
        List<List<String>> rows = readCsv();
        if (!rows.getFirst().equals(EXPECTED_HEADER)) {
            throw new IllegalStateException("Unexpected header in " + CSV_RESOURCE + ": " + rows.getFirst());
        }

        String insertSql = "INSERT INTO theaters (osm_id, name, brand, address, city, postal_code, lat, lng, h3_cell) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = context.getConnection().prepareStatement(insertSql)) {
            for (List<String> row : rows.subList(1, rows.size())) {
                double lat = Double.parseDouble(row.get(6));
                double lng = Double.parseDouble(row.get(7));
                statement.setString(1, row.get(0));
                statement.setString(2, row.get(1));
                for (int column = 2; column <= 5; column++) {
                    setNullableString(statement, column + 1, row.get(column));
                }
                statement.setDouble(7, lat);
                statement.setDouble(8, lng);
                statement.setLong(9, H3Cells.cellFor(lat, lng));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void setNullableString(PreparedStatement statement, int parameterIndex, String value)
            throws java.sql.SQLException {
        if (value.isBlank()) {
            statement.setNull(parameterIndex, Types.VARCHAR);
        } else {
            statement.setString(parameterIndex, value);
        }
    }

    private static List<List<String>> readCsv() throws IOException {
        InputStream csvStream = V16__seed_ontario_theaters.class.getClassLoader().getResourceAsStream(CSV_RESOURCE);
        if (csvStream == null) {
            throw new IllegalStateException(CSV_RESOURCE + " not found on the classpath");
        }
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    rows.add(parseCsvLine(line));
                }
            }
        }
        return rows;
    }

    /**
     * Splits one line written by Python's csv module: fields containing commas or quotes are
     * wrapped in double quotes, with embedded quotes doubled. No field in this file spans lines.
     *
     * @param line one CSV line
     * @return the line's fields, unquoted
     */
    static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean insideQuotes = false;
        for (int position = 0; position < line.length(); position++) {
            char character = line.charAt(position);
            if (insideQuotes) {
                if (character == '"' && position + 1 < line.length() && line.charAt(position + 1) == '"') {
                    field.append('"');
                    position++;
                } else if (character == '"') {
                    insideQuotes = false;
                } else {
                    field.append(character);
                }
            } else if (character == '"') {
                insideQuotes = true;
            } else if (character == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(character);
            }
        }
        fields.add(field.toString());
        return fields;
    }
}
