package com.waleed.naseej.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    private Instant timestamp;
    private int status;
    private String message;
    private String path;
    private Map<String, String> errors;

    public static ApiErrorResponse of(int status, String message, String path) {
        return ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status)
                .message(message)
                .path(path)
                .build();
    }

    public static ApiErrorResponse validation(int status, String message, String path, Map<String, String> errors) {
        return ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status)
                .message(message)
                .path(path)
                .errors(errors)
                .build();
    }
}
