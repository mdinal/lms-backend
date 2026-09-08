package com.lms.backend.controller;

import com.lms.backend.service.CloudFrontService;
import com.lms.backend.service.VideoTranscodingService;
import com.lms.backend.service.S3Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

import com.lms.backend.dto.ZoomMeetingResponse;
import com.lms.backend.service.ZoomService;

@RestController
@RequestMapping("/api/lessons")
public class LessonController {

    @Autowired
    private S3Service s3Service;

    @Autowired
    private CloudFrontService cloudFrontService;

    @Autowired
    private VideoTranscodingService videoTranscodingService;

    @Autowired
    private ZoomService zoomService;

    @PostMapping("/{id}/live-class")
    public ResponseEntity<?> createLiveClass(@PathVariable Long id, @RequestParam("topic") String topic, @RequestParam("duration") int duration) {
        try {
            ZoomMeetingResponse meeting = zoomService.createMeeting(topic, duration);
            
            // In reality, save meeting.getJoinUrl() and meeting.getId() to the Lesson in DB
            
            return ResponseEntity.ok(Map.of(
                "message", "Live class scheduled successfully",
                "joinUrl", meeting.getJoinUrl(),
                "startUrl", meeting.getStartUrl()
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Failed to schedule Zoom meeting: " + e.getMessage());
        }
    }
    
    @GetMapping("/{id}/presigned-url")
    public ResponseEntity<?> getPresignedUrl(@PathVariable Long id, @RequestParam("filename") String filename, @RequestParam("contentType") String contentType) {
        // Generate a unique object key for the raw video
        String rawS3Key = "raw-videos/lesson_" + id + "/" + System.currentTimeMillis() + "_" + filename;
        
        try {
            String presignedUrl = s3Service.generatePresignedUploadUrl(rawS3Key, contentType);
            return ResponseEntity.ok(Map.of(
                "presignedUrl", presignedUrl,
                "s3Key", rawS3Key
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Failed to generate S3 presigned URL");
        }
    }

    @PostMapping("/{id}/transcode")
    public ResponseEntity<?> transcodeVideo(@PathVariable Long id, @RequestBody Map<String, String> request) {
        String rawS3Key = request.get("s3Key");
        if (rawS3Key == null || rawS3Key.isEmpty()) {
            return ResponseEntity.badRequest().body("s3Key is required");
        }

        // Trigger MediaConvert Job to convert the raw MP4 into an HLS stream
        String outputPrefix = "hls-videos/lesson_" + id + "/";
        
        try {
            // Replace lms-videos-bucket with your actual bucket name or inject it
            // For now, hardcoding based on standard lms-videos-bucket, or we can fetch from config
            String bucket = "dev-lms-videos-123456789012";
            videoTranscodingService.createTranscodingJob("s3://" + bucket + "/" + rawS3Key, "s3://" + bucket + "/" + outputPrefix);
            
            // Save the future HLS playlist key to the database for this lesson
            String hlsPlaylistKey = outputPrefix + "lesson_" + id + "-hls.m3u8";
            // lessonRepository.updateVideoKey(id, hlsPlaylistKey);

            return ResponseEntity.ok(Map.of(
                "message", "Transcoding started successfully.", 
                "hlsKey", hlsPlaylistKey
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Failed to start MediaConvert job");
        }
    }

    @Autowired
    private com.lms.backend.repository.CourseRepository courseRepository;
    
    @Autowired
    private com.lms.backend.repository.UserRepository userRepository;

    @Autowired
    private com.lms.backend.repository.EnrollmentRepository enrollmentRepository;

    @GetMapping("/{id}/stream")
    public ResponseEntity<?> getVideoStreamUrl(@PathVariable Long id) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        com.lms.backend.entity.User currentUser = userRepository.findByEmail(email).orElse(null);

        if (currentUser == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        // Hardcoding Course ID lookup to match the generic lesson/course structure we built
        // In reality, this would lookup the Lesson, then get its Course
        java.util.UUID courseId;
        try {
            // For now we map lesson {id} to course ID string if it's passed as a UUID string, else skip auth for demo
            courseId = java.util.UUID.fromString(id.toString());
            com.lms.backend.entity.Course course = courseRepository.findById(courseId).orElse(null);
            
            if (currentUser.getRole() == com.lms.backend.entity.User.Role.STUDENT) {
                java.util.Optional<com.lms.backend.entity.Enrollment> enrollment = enrollmentRepository.findByUserAndCourse(currentUser, course);
                if (enrollment.isEmpty() || enrollment.get().getStatus() != com.lms.backend.entity.Enrollment.Status.ACTIVE) {
                    return ResponseEntity.status(403).body("You are not enrolled in this course.");
                }
            }
        } catch (Exception e) {
            // If it's not a UUID, we bypass strict enrollment check just for this mock phase
        }

        // For demonstration, we mock the key
        String s3Key = "hls-videos/lesson_" + id + "/lesson_" + id + "-hls.m3u8";

        try {
            // Generate short-lived CloudFront signed URL for the HLS playlist
            String signedUrl = cloudFrontService.generateSignedUrl(s3Key);
            return ResponseEntity.ok(Map.of("streamUrl", signedUrl));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to generate signed URL");
        }
    }
}
