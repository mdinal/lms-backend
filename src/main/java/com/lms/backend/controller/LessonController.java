package com.lms.backend.controller;

import com.lms.backend.dto.ZoomMeetingResponse;
import com.lms.backend.entity.Course;
import com.lms.backend.entity.Enrollment;
import com.lms.backend.entity.Lesson;
import com.lms.backend.entity.User;
import com.lms.backend.repository.CourseRepository;
import com.lms.backend.repository.EnrollmentRepository;
import com.lms.backend.repository.LessonRepository;
import com.lms.backend.repository.UserRepository;
import com.lms.backend.service.CloudFrontService;
import com.lms.backend.service.S3Service;
import com.lms.backend.service.VideoTranscodingService;
import com.lms.backend.service.ZoomService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/lessons")
public class LessonController {

    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final S3Service s3Service;
    private final CloudFrontService cloudFrontService;
    private final VideoTranscodingService videoTranscodingService;
    private final ZoomService zoomService;

    @Value("${aws.s3.bucket-name:dev-lms-videos-123456789012}")
    private String s3BucketName;

    public LessonController(LessonRepository lessonRepository, CourseRepository courseRepository,
                            UserRepository userRepository, EnrollmentRepository enrollmentRepository,
                            S3Service s3Service, CloudFrontService cloudFrontService,
                            VideoTranscodingService videoTranscodingService, ZoomService zoomService) {
        this.lessonRepository = lessonRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.s3Service = s3Service;
        this.cloudFrontService = cloudFrontService;
        this.videoTranscodingService = videoTranscodingService;
        this.zoomService = zoomService;
    }

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    @PostMapping("/course/{courseId}")
    public ResponseEntity<?> createLesson(@PathVariable UUID courseId, @RequestBody Map<String, String> request) {
        User currentUser = getAuthenticatedUser();
        if (currentUser == null || (currentUser.getRole() != User.Role.ADMIN && currentUser.getRole() != User.Role.TUTOR)) {
            return ResponseEntity.status(403).body("Forbidden");
        }

        Course course = courseRepository.findById(courseId).orElse(null);
        if (course == null) {
            return ResponseEntity.status(404).body("Course not found");
        }

        if (currentUser.getRole() == User.Role.TUTOR && course.getTutor() != null && !course.getTutor().getId().equals(currentUser.getId())) {
            return ResponseEntity.status(403).body("You can only add lessons to your own courses");
        }

        String title = request.get("title");
        if (title == null || title.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Lesson title is required");
        }

        Lesson lesson = new Lesson();
        lesson.setTitle(title.trim());
        lesson.setCourse(course);

        if (request.containsKey("scheduledAt") && request.get("scheduledAt") != null && !request.get("scheduledAt").isEmpty()) {
            try {
                lesson.setScheduledAt(LocalDateTime.parse(request.get("scheduledAt")));
            } catch (Exception ignored) {}
        }

        lessonRepository.save(lesson);
        return ResponseEntity.ok(Map.of("message", "Lesson created successfully", "id", lesson.getId().toString()));
    }

    @GetMapping("/course/{courseId}")
    public ResponseEntity<?> getLessonsForCourse(@PathVariable UUID courseId) {
        Course course = courseRepository.findById(courseId).orElse(null);
        if (course == null) {
            return ResponseEntity.status(404).body("Course not found");
        }

        List<Lesson> lessons = lessonRepository.findByCourseOrderByScheduledAtAsc(course);
        List<Map<String, Object>> result = lessons.stream().map(l -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", l.getId().toString());
            map.put("title", l.getTitle());
            map.put("videoS3Key", l.getVideoS3Key());
            map.put("documentS3Key", l.getDocumentS3Key());
            map.put("zoomJoinUrl", l.getZoomJoinUrl());
            map.put("zoomMeetingId", l.getZoomMeetingId());
            map.put("scheduledAt", l.getScheduledAt() != null ? l.getScheduledAt().toString() : null);
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateLesson(@PathVariable UUID id, @RequestBody Map<String, String> request) {
        User currentUser = getAuthenticatedUser();
        if (currentUser == null || (currentUser.getRole() != User.Role.ADMIN && currentUser.getRole() != User.Role.TUTOR)) {
            return ResponseEntity.status(403).body("Forbidden");
        }

        Lesson lesson = lessonRepository.findById(id).orElse(null);
        if (lesson == null) {
            return ResponseEntity.status(404).body("Lesson not found");
        }

        if (request.containsKey("title")) lesson.setTitle(request.get("title"));
        if (request.containsKey("documentS3Key")) lesson.setDocumentS3Key(request.get("documentS3Key"));
        if (request.containsKey("scheduledAt")) {
            try {
                lesson.setScheduledAt(LocalDateTime.parse(request.get("scheduledAt")));
            } catch (Exception ignored) {}
        }

        lessonRepository.save(lesson);
        return ResponseEntity.ok(Map.of("message", "Lesson updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteLesson(@PathVariable UUID id) {
        User currentUser = getAuthenticatedUser();
        if (currentUser == null || (currentUser.getRole() != User.Role.ADMIN && currentUser.getRole() != User.Role.TUTOR)) {
            return ResponseEntity.status(403).body("Forbidden");
        }

        Lesson lesson = lessonRepository.findById(id).orElse(null);
        if (lesson == null) {
            return ResponseEntity.status(404).body("Lesson not found");
        }

        lessonRepository.delete(lesson);
        return ResponseEntity.ok(Map.of("message", "Lesson deleted successfully"));
    }

    @PostMapping("/{id}/live-class")
    public ResponseEntity<?> createLiveClass(@PathVariable UUID id,
                                             @RequestParam("topic") String topic,
                                             @RequestParam(value = "duration", defaultValue = "60") int duration) {
        User currentUser = getAuthenticatedUser();
        if (currentUser == null || (currentUser.getRole() != User.Role.ADMIN && currentUser.getRole() != User.Role.TUTOR)) {
            return ResponseEntity.status(403).body("Forbidden");
        }

        Lesson lesson = lessonRepository.findById(id).orElse(null);
        if (lesson == null) {
            return ResponseEntity.status(404).body("Lesson not found");
        }

        try {
            ZoomMeetingResponse meeting = zoomService.createMeeting(topic, duration);

            // Persist the meeting details directly to the Lesson in DB
            lesson.setZoomJoinUrl(meeting.getJoinUrl());
            lesson.setZoomMeetingId(meeting.getId() != null ? meeting.getId().toString() : null);
            if (lesson.getScheduledAt() == null) {
                lesson.setScheduledAt(LocalDateTime.now());
            }
            lessonRepository.save(lesson);

            return ResponseEntity.ok(Map.of(
                "message", "Live class scheduled successfully",
                "joinUrl", meeting.getJoinUrl(),
                "startUrl", meeting.getStartUrl(),
                "meetingId", meeting.getId() != null ? meeting.getId().toString() : ""
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to schedule Zoom meeting: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/presigned-url")
    public ResponseEntity<?> getPresignedUrl(@PathVariable UUID id,
                                             @RequestParam("filename") String filename,
                                             @RequestParam("contentType") String contentType) {
        Lesson lesson = lessonRepository.findById(id).orElse(null);
        if (lesson == null) {
            return ResponseEntity.status(404).body("Lesson not found");
        }

        String rawS3Key = "raw-videos/lesson_" + id + "/" + System.currentTimeMillis() + "_" + filename;

        try {
            String presignedUrl = s3Service.generatePresignedUploadUrl(rawS3Key, contentType);
            return ResponseEntity.ok(Map.of(
                "presignedUrl", presignedUrl,
                "s3Key", rawS3Key
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to generate S3 presigned URL: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/transcode")
    public ResponseEntity<?> transcodeVideo(@PathVariable UUID id, @RequestBody Map<String, String> request) {
        String rawS3Key = request.get("s3Key");
        if (rawS3Key == null || rawS3Key.isEmpty()) {
            return ResponseEntity.badRequest().body("s3Key is required");
        }

        Lesson lesson = lessonRepository.findById(id).orElse(null);
        if (lesson == null) {
            return ResponseEntity.status(404).body("Lesson not found");
        }

        String outputPrefix = "hls-videos/lesson_" + id + "/";

        try {
            videoTranscodingService.createTranscodingJob(
                "s3://" + s3BucketName + "/" + rawS3Key,
                "s3://" + s3BucketName + "/" + outputPrefix
            );

            String hlsPlaylistKey = outputPrefix + "lesson_" + id + "-hls.m3u8";
            lesson.setVideoS3Key(hlsPlaylistKey);
            lessonRepository.save(lesson);

            return ResponseEntity.ok(Map.of(
                "message", "Transcoding started successfully.",
                "hlsKey", hlsPlaylistKey
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to start MediaConvert job: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/attach-recording")
    public ResponseEntity<?> attachRecording(@PathVariable UUID id, @RequestBody Map<String, String> payload) {
        User currentUser = getAuthenticatedUser();
        if (currentUser == null || (currentUser.getRole() != User.Role.ADMIN && currentUser.getRole() != User.Role.TUTOR)) {
            return ResponseEntity.status(403).body("Forbidden");
        }

        Lesson lesson = lessonRepository.findById(id).orElse(null);
        if (lesson == null) {
            return ResponseEntity.status(404).body("Lesson not found");
        }

        String s3Key = payload.get("s3Key");
        if (s3Key == null || s3Key.trim().isEmpty()) {
            s3Key = payload.get("videoS3Key");
        }
        if (s3Key == null || s3Key.trim().isEmpty()) {
            s3Key = payload.get("recordingUrl");
        }
        if (s3Key == null || s3Key.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("s3Key, videoS3Key, or recordingUrl is required");
        }

        lesson.setVideoS3Key(s3Key.trim());
        lessonRepository.save(lesson);

        return ResponseEntity.ok(Map.of(
            "message", "Recording attached successfully to lesson",
            "lessonId", lesson.getId().toString(),
            "videoS3Key", lesson.getVideoS3Key()
        ));
    }

    @GetMapping("/{id}/stream")
    public ResponseEntity<?> getVideoStreamUrl(@PathVariable UUID id) {
        User currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        Lesson lesson = lessonRepository.findById(id).orElse(null);
        if (lesson == null) {
            return ResponseEntity.status(404).body("Lesson not found");
        }

        Course course = lesson.getCourse();

        // Check access permissions
        if (currentUser.getRole() == User.Role.STUDENT) {
            Optional<Enrollment> enrollment = enrollmentRepository.findByUserAndCourse(currentUser, course);
            if (enrollment.isEmpty() || enrollment.get().getStatus() != Enrollment.Status.ACTIVE) {
                return ResponseEntity.status(403).body("You are not enrolled in this course");
            }
        } else if (currentUser.getRole() == User.Role.TUTOR && course.getTutor() != null && !course.getTutor().getId().equals(currentUser.getId())) {
            return ResponseEntity.status(403).body("You are not the instructor for this course");
        }

        String s3Key = lesson.getVideoS3Key();
        if (s3Key == null || s3Key.isEmpty()) {
            return ResponseEntity.status(404).body("No video recorded or transcoded for this lesson yet");
        }

        try {
            String signedUrl = cloudFrontService.generateSignedUrl(s3Key);
            return ResponseEntity.ok(Map.of("streamUrl", signedUrl));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to generate signed streaming URL: " + e.getMessage());
        }
    }

    @GetMapping("/live-classes/upcoming")
    public ResponseEntity<?> getUpcomingLiveClasses() {
        User currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        List<Course> relevantCourses;
        if (currentUser.getRole() == User.Role.STUDENT) {
            relevantCourses = enrollmentRepository.findByUser(currentUser).stream()
                .filter(e -> e.getStatus() == Enrollment.Status.ACTIVE)
                .map(Enrollment::getCourse)
                .collect(Collectors.toList());
        } else if (currentUser.getRole() == User.Role.TUTOR) {
            relevantCourses = courseRepository.findByTutor(currentUser);
        } else {
            relevantCourses = courseRepository.findAll();
        }

        List<Map<String, Object>> liveClasses = new ArrayList<>();
        for (Course course : relevantCourses) {
            List<Lesson> lessons = lessonRepository.findByCourse(course);
            for (Lesson lesson : lessons) {
                if (lesson.getZoomJoinUrl() != null && !lesson.getZoomJoinUrl().isEmpty()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("lessonId", lesson.getId().toString());
                    map.put("lessonTitle", lesson.getTitle());
                    map.put("courseId", course.getId().toString());
                    map.put("courseTitle", course.getTitle());
                    map.put("zoomJoinUrl", lesson.getZoomJoinUrl());
                    map.put("zoomMeetingId", lesson.getZoomMeetingId());
                    map.put("scheduledAt", lesson.getScheduledAt() != null ? lesson.getScheduledAt().toString() : null);
                    liveClasses.add(map);
                }
            }
        }

        return ResponseEntity.ok(liveClasses);
    }
}
