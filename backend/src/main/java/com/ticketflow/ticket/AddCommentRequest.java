package com.ticketflow.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

public record AddCommentRequest(
        @NotBlank(message = "Author name is required")
        @Size(max = 100, message = "Author name cannot exceed 100 characters")
        @JsonDeserialize(using = TextDeserializer.class)
        String authorName,
        @NotBlank(message = "Content is required")
        @Size(max = 2000, message = "Content cannot exceed 2000 characters")
        @JsonDeserialize(using = TextDeserializer.class)
        String content,
        @NotNull(message = "Expected version is required")
        @PositiveOrZero(message = "Expected version must be nonnegative")
        @JsonDeserialize(using = AssignTicketRequest.ExpectedVersionDeserializer.class)
        Long expectedVersion
) {
    public AddCommentRequest {
        authorName = authorName == null ? null : authorName.strip();
        content = content == null ? null : content.strip();
    }

    public static class TextDeserializer extends StdDeserializer<String> {
        public TextDeserializer() { super(String.class); }

        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return context.reportInputMismatch(String.class, "Comment text must be a JSON string");
            }
            return parser.getString();
        }
    }
}
