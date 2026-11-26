package com.ticketflow.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.deser.std.StdDeserializer;

public record AssignTicketRequest(
        @NotBlank(message = "Agent ID is required")
        @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
                message = "Agent ID must be a UUID string")
        String agentId,

        @NotNull(message = "Expected version is required")
        @PositiveOrZero(message = "Expected version must be nonnegative")
        @JsonDeserialize(using = ExpectedVersionDeserializer.class)
        Long expectedVersion
) {
    // Keep strict integer handling local to this request; do not change other APIs.
    public static class ExpectedVersionDeserializer extends StdDeserializer<Long> {

        public ExpectedVersionDeserializer() {
            super(Long.class);
        }

        @Override
        public Long deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return context.reportInputMismatch(Long.class,
                        "Expected version must be a JSON integer");
            }
            return parser.getLongValue();
        }
    }
}
