package com.schoolable.backend.attendance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.CompareFacesRequest;
import software.amazon.awssdk.services.rekognition.model.CompareFacesResponse;
import software.amazon.awssdk.services.rekognition.model.Image;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;

@Service
public class AwsRekognitionFaceMatchService {

    private final RekognitionClient rekognitionClient;
    private final double similarityThreshold;

    public AwsRekognitionFaceMatchService(
            @Value("${face.match.aws.region:us-east-1}") String region,
            @Value("${face.match.threshold:85}") double similarityThreshold) {
        this.rekognitionClient = RekognitionClient.builder()
            .region(Region.of(region))
            .build();
        this.similarityThreshold = similarityThreshold;
    }

    public FaceMatchResult compare(String referenceUrl, String candidateUrl) {
        try {
            byte[] sourceBytes = download(referenceUrl);
            byte[] targetBytes = download(candidateUrl);

            CompareFacesRequest request = CompareFacesRequest.builder()
                .sourceImage(Image.builder().bytes(SdkBytes.fromByteArray(sourceBytes)).build())
                .targetImage(Image.builder().bytes(SdkBytes.fromByteArray(targetBytes)).build())
                .similarityThreshold((float) similarityThreshold)
                .build();

            CompareFacesResponse response = rekognitionClient.compareFaces(request);
            if (response.faceMatches() == null || response.faceMatches().isEmpty()) {
                return new FaceMatchResult(false, 0.0, "aws", "No match");
            }

            double similarity = response.faceMatches().get(0).similarity();
            boolean match = similarity >= similarityThreshold;
            return new FaceMatchResult(match, similarity, "aws", match ? "Matched" : "Below threshold");
        } catch (Exception e) {
            return new FaceMatchResult(false, 0.0, "aws", e.getMessage());
        }
    }

    private byte[] download(String url) throws Exception {
        try (InputStream inputStream = new URL(url).openStream();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            return outputStream.toByteArray();
        }
    }
}
