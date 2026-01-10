package com.schoolable.backend.config;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard API Response Wrapper
 * Use ResponseEntity<ApiResponse<T>> for consistent API responses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private Object meta;

    // Constructors
    private ApiResponse() {}

    private ApiResponse(boolean success, String message, T data, Object meta) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.meta = meta;
    }

    // Static factory methods
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, null, data, null);
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data, null);
    }

    public static <T> ApiResponse<T> success(T data, Object meta) {
        return new ApiResponse<>(true, null, data, meta);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, null);
    }

    public static <T> ApiResponse<T> error(String message, T errorDetails) {
        return new ApiResponse<>(false, message, errorDetails, null);
    }

    // Getters
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public Object getMeta() { return meta; }

    // Builder pattern for complex responses
    public static class Builder<T> {
        private boolean success = true;
        private String message;
        private T data;
        private Object meta;

        public Builder<T> success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder<T> message(String message) {
            this.message = message;
            return this;
        }

        public Builder<T> data(T data) {
            this.data = data;
            return this;
        }

        public Builder<T> meta(Object meta) {
            this.meta = meta;
            return this;
        }

        public ApiResponse<T> build() {
            return new ApiResponse<>(success, message, data, meta);
        }
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }
}
