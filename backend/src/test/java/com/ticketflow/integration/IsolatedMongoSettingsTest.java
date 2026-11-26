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
        return new HashMap<>(Map.of("TICKETFLOW_TEST_DB_AUTHORIZED", "true",
                "TICKETFLOW_TEST_DATABASE", DB, "TICKETFLOW_TEST_DATABASE_CONFIRMATION", DB,
                "TICKETFLOW_TEST_MONGODB_URI", "mongodb://127.0.0.1/" + DB + "?w=majority"));
    }

    @Test
    void parsesOnlyExplicitlyConfirmedTargetWithoutOpeningAClient() {
        assertEquals(DB, IsolatedMongoSettings.require(valid(), true).database());
        assertEquals("IsolatedMongoSettings[redacted]", IsolatedMongoSettings.require(valid(), true).toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TICKETFLOW_TEST_DB_AUTHORIZED", "TICKETFLOW_TEST_DATABASE",
            "TICKETFLOW_TEST_DATABASE_CONFIRMATION", "TICKETFLOW_TEST_MONGODB_URI"})
    void missingConfigurationFailsClosed(String key) {
        Map<String, String> config = valid(); config.remove(key);
        assertThrows(IllegalStateException.class, () -> IsolatedMongoSettings.require(config, true));
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
        Map<String, String> config = valid(); config.put("TICKETFLOW_TEST_MONGODB_URI", uri.replace("/DB", "/" + DB));
        var ex = assertThrows(IllegalStateException.class, () -> IsolatedMongoSettings.require(config, true));
        assertFalse(ex.getMessage().contains(uri)); assertNull(ex.getCause());
    }

    @Test
    void mismatchedConfirmationOrNonTestDatabaseIsRejected() {
        Map<String, String> config = valid(); config.put("TICKETFLOW_TEST_DATABASE_CONFIRMATION", "shared");
        assertThrows(IllegalStateException.class, () -> IsolatedMongoSettings.require(config, true));
        config.put("TICKETFLOW_TEST_DATABASE", "shared");
        assertThrows(IllegalStateException.class, () -> IsolatedMongoSettings.require(config, true));
    }
}
