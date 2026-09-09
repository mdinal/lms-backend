package com.lms.backend.controller;

import com.lms.backend.entity.Lesson;
import com.lms.backend.repository.LessonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/webhooks/zoom")
public class ZoomWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(ZoomWebhookController.class);

    @Value("${zoom.webhookSecret:}")
    private String zoomWebhookSecret;

    private final LessonRepository lessonRepository;

    public ZoomWebhookController(LessonRepository lessonRepository) {
        this.lessonRepository = lessonRepository;
    }

    @PostMapping
    public ResponseEntity<?> handleZoomWebhook(@RequestBody Map<String, Object> body,
                                              @RequestHeader Map<String, String> headers) {
        String event = (String) body.get("event");
        logger.info("Received Zoom webhook event: {}", event);

        // 1. Zoom CRC (Challenge-Response Check) URL validation
        if ("endpoint.url_validation".equals(event)) {
            Map<String, Object> payload = (Map<String, Object>) body.get("payload");
            if (payload != null && payload.containsKey("plainToken")) {
                String plainToken = (String) payload.get("plainToken");
                String encryptedToken = generateHmacSha256(plainToken, zoomWebhookSecret);
                logger.info("Handled Zoom endpoint.url_validation challenge");
                return ResponseEntity.ok(Map.of(
                    "plainToken", plainToken,
                    "encryptedToken", encryptedToken
                ));
            }
        }

        // 2. Zoom Cloud Recording Completed Event
        if ("recording.completed".equals(event)) {
            Map<String, Object> payload = (Map<String, Object>) body.get("payload");
            if (payload != null && payload.containsKey("object")) {
                Map<String, Object> object = (Map<String, Object>) payload.get("object");
                Object idObj = object.get("id");
                String meetingId = idObj != null ? idObj.toString().trim() : null;

                if (meetingId != null) {
                    // Extract MP4 recording play or download URL
                    String recordingUrl = null;
                    List<Map<String, Object>> recordingFiles = (List<Map<String, Object>>) object.get("recording_files");
                    if (recordingFiles != null) {
                        for (Map<String, Object> file : recordingFiles) {
                            String fileType = (String) file.get("file_type");
                            if ("MP4".equalsIgnoreCase(fileType)) {
                                String playUrl = (String) file.get("play_url");
                                String downloadUrl = (String) file.get("download_url");
                                recordingUrl = (playUrl != null && !playUrl.isEmpty()) ? playUrl : downloadUrl;
                                break;
                            }
                        }
                    }

                    if (recordingUrl != null) {
                        // Find matching lesson by zoomMeetingId
                        Optional<Lesson> lessonOpt = lessonRepository.findByZoomMeetingId(meetingId);
                        
                        // If not found by exact string, try normalized match (without spaces/dashes)
                        if (lessonOpt.isEmpty()) {
                            String normalizedId = meetingId.replaceAll("[^0-9]", "");
                            lessonOpt = lessonRepository.findAll().stream()
                                    .filter(l -> l.getZoomMeetingId() != null && 
                                            l.getZoomMeetingId().replaceAll("[^0-9]", "").equals(normalizedId))
                                    .findFirst();
                        }

                        if (lessonOpt.isPresent()) {
                            Lesson lesson = lessonOpt.get();
                            lesson.setVideoS3Key(recordingUrl);
                            lessonRepository.save(lesson);
                            logger.info("Successfully linked Zoom recording to Lesson ID: {} for meeting ID: {}", lesson.getId(), meetingId);
                            return ResponseEntity.ok(Map.of(
                                "status", "SUCCESS",
                                "message", "Recording attached to lesson",
                                "lessonId", lesson.getId().toString()
                            ));
                        } else {
                            logger.warn("Received Zoom recording.completed but no matching lesson found for meeting ID: {}", meetingId);
                        }
                    }
                }
            }
        }

        return ResponseEntity.ok(Map.of("status", "RECEIVED"));
    }

    private String generateHmacSha256(String data, String key) {
        if (key == null || key.isEmpty()) {
            key = "default_secret";
        }
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hash = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            logger.error("Failed to generate HMAC SHA256", e);
            return "";
        }
    }
}
