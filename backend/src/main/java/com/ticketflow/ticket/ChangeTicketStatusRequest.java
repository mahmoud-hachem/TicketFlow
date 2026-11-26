package com.ticketflow.ticket;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.deser.std.StdDeserializer;

public record ChangeTicketStatusRequest(
        @NotNull(message = "Status is required")
        @JsonDeserialize(using = StatusDeserializer.class)
        TicketStatus status,
        @NotNull(message = "Expected version is required")
        @PositiveOrZero(message = "Expected version must be nonnegative")
        @JsonDeserialize(using = AssignTicketRequest.ExpectedVersionDeserializer.class)
        Long expectedVersion
) {
    public static class StatusDeserializer extends StdDeserializer<TicketStatus> {
        public StatusDeserializer() {
            super(TicketStatus.class);
        }

        @Override
        public TicketStatus deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.hasToken(JsonToken.VALUE_STRING)) {
                try {
                    return TicketStatus.valueOf(parser.getString());
                } catch (IllegalArgumentException ex) {
                    return context.reportInputMismatch(TicketStatus.class, "Unsupported status");
                }
            }
            return context.reportInputMismatch(TicketStatus.class, "Status must be a JSON string");
        }
    }
}
