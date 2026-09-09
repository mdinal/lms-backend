package com.lms.backend.controller;

import com.lms.backend.entity.Lesson;
import com.lms.backend.repository.LessonRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/webhooks")
public class MediaConvertWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(MediaConvertWebhookController.class);
    private final LessonRepository lessonRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MediaConvertWebhookController(LessonRepository lessonRepository) {
        this.lessonRepository = lessonRepository;
    }

    @PostMapping("/mediaconvert")
    public ResponseEntity<?> handleMediaConvertEvent(@RequestBody String rawPayload) {
        logger.info("Received MediaConvert webhook: {}", rawPayload);

        try {
            JsonNode rootNode = objectMapper.readTree(rawPayload);

            // Handle SNS Subscription Confirmation if needed
            if (rootNode.has("Type") && "SubscriptionConfirmation".equals(rootNode.get("Type").asText())) {
                String subscribeUrl = rootNode.get("SubscribeURL").asText();
                logger.info("Confirming SNS subscription via URL: {}", subscribeUrl);
                return ResponseEntity.ok("Subscription confirmation received");
            }

            // Extract inner message if wrapped in SNS
            JsonNode detailNode = rootNode;
            if (rootNode.has("Message")) {
                detailNode = objectMapper.readTree(rootNode.get("Message").asText());
            }
            if (detailNode.has("detail")) {
                detailNode = detailNode.get("detail");
            }

            String status = detailNode.has("status") ? detailNode.get("status").asText() : "";
            logger.info("MediaConvert job status: {}", status);

            if ("COMPLETE".equalsIgnoreCase(status)) {
                // Check if userMetadata contains lessonId
                if (detailNode.has("userMetadata") && detailNode.get("userMetadata").has("lessonId")) {
                    String lessonIdStr = detailNode.get("userMetadata").get("lessonId").asText();
                    UUID lessonId = UUID.fromString(lessonIdStr);
                    Lesson lesson = lessonRepository.findById(lessonId).orElse(null);
                    if (lesson != null) {
                        String hlsKey = "hls-videos/lesson_" + lessonId + "/lesson_" + lessonId + "-hls.m3u8";
                        lesson.setVideoS3Key(hlsKey);
                        lessonRepository.save(lesson);
                        logger.info("Updated lesson {} with videoS3Key {}", lessonId, hlsKey);
                    }
                }
            }

            return ResponseEntity.ok(Map.of("status", "processed"));
        } catch (Exception e) {
            logger.error("Error processing MediaConvert webhook", e);
            return ResponseEntity.ok(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
