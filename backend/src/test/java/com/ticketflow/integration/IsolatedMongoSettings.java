package com.ticketflow.integration;

import com.mongodb.ConnectionString;
import java.util.Map;

public record IsolatedMongoSettings(String uri, String database) {
    public static IsolatedMongoSettings require(Map<String, String> environment, boolean enabled) {
        if (!enabled || !"true".equals(environment.get("TICKETFLOW_TEST_DB_AUTHORIZED"))) {
            throw new IllegalStateException("Database tests require explicit profile activation and human authorization");
        }
        String database = environment.get("TICKETFLOW_TEST_DATABASE");
        // IT-01: the owner also authorized this exact dedicated database name.
        if (database == null || !(database.equals("ticketflow_test") || database.matches("ticketflow_it_[a-f0-9]{32}"))
                || !database.equals(environment.get("TICKETFLOW_TEST_DATABASE_CONFIRMATION"))) {
            throw new IllegalStateException("An explicitly confirmed isolated test database is required");
        }
        String uri = environment.get("TICKETFLOW_TEST_MONGODB_URI");
        try {
            ConnectionString parsed = new ConnectionString(uri == null ? "" : uri);
            if (!database.equals(parsed.getDatabase()) || parsed.getWriteConcern() == null
                    || !"majority".equals(parsed.getWriteConcern().getWString())
                    || parsed.getReadPreference() != null
                    && !"primary".equals(parsed.getReadPreference().getName())) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException ex) {
            // Never include the supplied URI or a parser exception/cause in diagnostics.
            throw new IllegalStateException("Test URI must target the confirmed database with w=majority and primary reads");
        }
        return new IsolatedMongoSettings(uri, database);
    }

    @Override
    public String toString() { return "IsolatedMongoSettings[redacted]"; }
}
