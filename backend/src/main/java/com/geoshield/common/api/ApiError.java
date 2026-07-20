package com.geoshield.common.api;

import java.time.Instant;

public record ApiError(boolean success, String message, Object data, String errorCode, Instant timestamp) {
    public static ApiError of(String message, String errorCode) {
        return new ApiError(false, message, null, errorCode, Instant.now());
    }
}
