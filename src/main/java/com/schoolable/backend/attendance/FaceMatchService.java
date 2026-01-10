package com.schoolable.backend.attendance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FaceMatchService {

    private final String provider;
    private final AwsRekognitionFaceMatchService awsService;

    public FaceMatchService(
            @Value("${face.match.provider:mock}") String provider,
            AwsRekognitionFaceMatchService awsService) {
        this.provider = provider;
        this.awsService = awsService;
    }

    public FaceMatchResult compare(String referenceUrl, String candidateUrl) {
        if (referenceUrl == null || candidateUrl == null) {
            return new FaceMatchResult(false, 0.0, provider, "Missing image URL");
        }

        if ("aws".equalsIgnoreCase(provider)) {
            return awsService.compare(referenceUrl, candidateUrl);
        }

        return new FaceMatchResult(true, 97.5, "mock", "Mock match");
    }
}
