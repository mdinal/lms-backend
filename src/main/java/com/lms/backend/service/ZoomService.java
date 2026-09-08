package com.lms.backend.service;

import com.lms.backend.dto.ZoomMeetingResponse;
import com.lms.backend.dto.ZoomOAuthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;
import java.util.Map;

@Service
public class ZoomService {

    @Value("${zoom.accountId:}")
    private String accountId;

    @Value("${zoom.clientId:}")
    private String clientId;

    @Value("${zoom.clientSecret:}")
    private String clientSecret;

    private final WebClient webClient;
    private String cachedToken = null;
    private long tokenExpiryTime = 0;

    public ZoomService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    private String getAccessToken() {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiryTime) {
            return cachedToken;
        }

        if (accountId == null || clientId == null || clientSecret == null || accountId.isEmpty()) {
            throw new RuntimeException("Zoom credentials are not configured properly.");
        }

        String authString = clientId + ":" + clientSecret;
        String base64Auth = Base64.getEncoder().encodeToString(authString.getBytes());

        ZoomOAuthResponse response = webClient.post()
                .uri("https://zoom.us/oauth/token?grant_type=account_credentials&account_id=" + accountId)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + base64Auth)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .retrieve()
                .bodyToMono(ZoomOAuthResponse.class)
                .block();

        if (response != null && response.getAccessToken() != null) {
            this.cachedToken = response.getAccessToken();
            // Zoom tokens expire in 1 hour (3600 seconds), buffer by 60 seconds
            this.tokenExpiryTime = System.currentTimeMillis() + ((response.getExpiresIn() - 60) * 1000);
            return cachedToken;
        }

        throw new RuntimeException("Failed to obtain Zoom access token.");
    }

    public ZoomMeetingResponse createMeeting(String topic, int durationMinutes) {
        String token = getAccessToken();

        Map<String, Object> meetingRequest = Map.of(
                "topic", topic,
                "type", 2, // Scheduled meeting
                "duration", durationMinutes,
                "settings", Map.of(
                        "host_video", true,
                        "participant_video", true,
                        "join_before_host", false,
                        "mute_upon_entry", true
                )
        );

        ZoomMeetingResponse response = webClient.post()
                .uri("https://api.zoom.us/v2/users/me/meetings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(meetingRequest)
                .retrieve()
                .bodyToMono(ZoomMeetingResponse.class)
                .block();

        return response;
    }
}
