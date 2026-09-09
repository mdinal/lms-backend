package com.lms.backend.controller;

import com.lms.backend.entity.Lesson;
import com.lms.backend.repository.LessonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ZoomWebhookControllerTest {

    @Mock
    private LessonRepository lessonRepository;

    private ZoomWebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new ZoomWebhookController(lessonRepository);
        ReflectionTestUtils.setField(controller, "zoomWebhookSecret", "test_secret_key");
    }

    @Test
    void testUrlValidationChallenge() {
        Map<String, Object> body = Map.of(
            "event", "endpoint.url_validation",
            "payload", Map.of("plainToken", "sample_token_123")
        );

        ResponseEntity<?> response = controller.handleZoomWebhook(body, Map.of());
        assertEquals(200, response.getStatusCode().value());

        Map<?, ?> resMap = (Map<?, ?>) response.getBody();
        assertNotNull(resMap);
        assertEquals("sample_token_123", resMap.get("plainToken"));
        assertNotNull(resMap.get("encryptedToken"));
        assertFalse(resMap.get("encryptedToken").toString().isEmpty());
    }

    @Test
    void testRecordingCompletedAttachesToLesson() {
        Lesson lesson = new Lesson();
        lesson.setId(UUID.randomUUID());
        lesson.setTitle("Calculus Live Session");
        lesson.setZoomMeetingId("98765432101");

        when(lessonRepository.findByZoomMeetingId("98765432101")).thenReturn(Optional.of(lesson));

        Map<String, Object> body = Map.of(
            "event", "recording.completed",
            "payload", Map.of(
                "object", Map.of(
                    "id", "98765432101",
                    "recording_files", List.of(
                        Map.of(
                            "file_type", "MP4",
                            "play_url", "https://zoom.us/rec/play/sample-play-url",
                            "download_url", "https://zoom.us/rec/download/sample-download-url"
                        )
                    )
                )
            )
        );

        ResponseEntity<?> response = controller.handleZoomWebhook(body, Map.of());
        assertEquals(200, response.getStatusCode().value());

        verify(lessonRepository).save(lesson);
        assertEquals("https://zoom.us/rec/play/sample-play-url", lesson.getVideoS3Key());
    }
}
