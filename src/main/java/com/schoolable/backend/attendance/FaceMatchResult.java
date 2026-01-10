package com.schoolable.backend.attendance;

public record FaceMatchResult(boolean match, double confidence, String provider, String message) {}
