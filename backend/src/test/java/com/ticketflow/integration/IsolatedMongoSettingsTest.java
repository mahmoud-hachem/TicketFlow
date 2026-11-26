package com.ticketflow.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class IsolatedMongoSettingsTest {
    private static final String DB = "ticketflow_it_0123456789abcdef0123456789abcdef";
    private Map<String, String> valid() {
        return valid(DB);
    }

    private Map<String, String> valid(String database) {
        return new HashMap<>(Map.of("TICKETFLOW_TEST_DB_AUTHORIZED", "true",
                "TICKETFLOW_TEST_DATABASE", database, "TICKETFLOW_TEST_DATABASE_CONFIRMATION", database,
                "TICKETFLOW_TEST_MONGODB_URI", "mongodb://127.0.0.1/" + database + "?w=majority"));
    }

    @ParameterizedTest
    @ValueSource(strings = {DB, "ticketflow_test"})
    void parsesOnlyExplicitlyConfirmedTargetWithoutOpeningAClient(String database) {
        assertEquals(database, IsolatedMongoSettings.require(valid(database), true).database());
        assertEquals("IsolatedMongoSettings[redacted]", IsolatedMongoSettings.require(valid(database), true).toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TICKETFLOW_TEST_DB_AUTHORIZED", "TICKETFLOW_TEST_DATABASE",
            "TICKETFLOW_TEST_DATABASE_CONFIRMATION", "TICKETFLOW_TEST_MONGODB_URI"})
    void missingConfigurationFailsClosed(String key) {
        for (String database : new String[] {DB, "ticketflow_test"}) {
            Map<String, String> config = valid(database); config.remove(key);
            assertThrows(IllegalStateException.class, () -> IsolatedMongoSettings.require(config, true));
        }
    }

    @Test
    void sharedDevelopmentConfigurationIsNeverAFallback() {
        assertThrows(IllegalStateException.class, () -> IsolatedMongoSettings.require(
                Map.of("MONGODB_URI", "mongodb://127.0.0.1/shared"), true));
        assertThrows(IllegalStateException.class, () -> IsolatedMongoSettings.require(valid(), false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"mongodb://127.0.0.1/shared?w=majority", "mongodb://127.0.0.1/?w=majority",
            "mongodb://127.0.0.1/DB?w=0", "mongodb://127.0.0.1/DB",
            "mongodb://127.0.0.1/DB?w=majority&readPreference=secondary", "SENSITIVE_INVALID_URI"})
    void unsafeUrisAreRejectedWithoutLeakingTheirContents(String uri) {
        for (String database : new String[] {DB, "ticketflow_test"}) {
            Map<String, String> config = valid(database);
            config.put("TICKETFLOW_TEST_MONGODB_URI", uri.replace("/DB", "/" + database));
            var ex = assertThrows(IllegalStateException.class, () -> IsolatedMongoSettings.require(config, true));
            assertFalse(ex.getMessage().contains(uri)); assertNull(ex.getCause());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ticketflow_dev", "shared", "ticketflow_test_other", "ticketflow_test ",
            "TICKETFLOW_TEST", "ticketflow_it_invalid"})
    void unauthorizedNamesFailEvenWithMatchingConfirmationAndUri(String database) {
        assertThrows(IllegalStateException.class, () -> IsolatedMongoSettings.require(valid(database), true));
    }

    @Test
    void dedicatedDatabaseStillRequiresAuthorizationActivationAndExactConfirmation() {
        Map<String, String> config = valid("ticketflow_test");
        assertThrows(IllegalStateException.class, () -> IsolatedMongoSettings.require(config, false));
        config.put("TICKETFLOW_TEST_DB_AUTHORIZED", "false");
        assertThrows(IllegalStateException.class, () -> IsolatedMongoSettings.require(config, true));
        config.put("TICKETFLOW_TEST_DB_AUTHORIZED", "true");
        config.put("TICKETFLOW_TEST_DATABASE_CONFIRMATION", DB);
        assertThrows(IllegalStateException.class, () -> IsolatedMongoSettings.require(config, true));
        config.put("TICKETFLOW_TEST_DATABASE_CONFIRMATION", "ticketflow_test");
        config.put("TICKETFLOW_TEST_MONGODB_URI", "mongodb://127.0.0.1/" + DB + "?w=majority");
        assertThrows(IllegalStateException.class, () -> IsolatedMongoSettings.require(config, true));
        config.put("TICKETFLOW_TEST_MONGODB_URI", "mongodb://127.0.0.1/ticketflow_test?w=majority&readPreference=primary");
        assertEquals("ticketflow_test", IsolatedMongoSettings.require(config, true).database());
    }

    @Test
    void mismatchedConfirmationOrNonTestDatabaseIsRejected() {
        Map<String, String> config = valid(); config.put("TICKETFLOW_TEST_DATABASE_CONFIRMATION", "shared");
        assertThrows(IllegalStateException.class, () -> IsolatedMongoSettings.require(config, true));
        config.put("TICKETFLOW_TEST_DATABASE", "shared");
        assertThrows(IllegalStateException.class, () -> IsolatedMongoSettings.require(config, true));
    }
}
