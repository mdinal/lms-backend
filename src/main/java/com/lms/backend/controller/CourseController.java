package com.lms.backend.controller;

import com.lms.backend.entity.Course;
import com.lms.backend.entity.Enrollment;
import com.lms.backend.entity.Lesson;
import com.lms.backend.entity.User;
import com.lms.backend.repository.CourseRepository;
import com.lms.backend.repository.EnrollmentRepository;
import com.lms.backend.repository.LessonRepository;
import com.lms.backend.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final LessonRepository lessonRepository;

    public CourseController(CourseRepository courseRepository, UserRepository userRepository,
                            EnrollmentRepository enrollmentRepository, LessonRepository lessonRepository) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.lessonRepository = lessonRepository;
    }

    @GetMapping
    public ResponseEntity<?> getCourses() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = null;
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            currentUser = userRepository.findByEmail(auth.getName()).orElse(null);
        }

        List<Course> courses;
        if (currentUser != null && currentUser.getRole() == User.Role.TUTOR) {
            courses = courseRepository.findByTutor(currentUser);
        } else {
            courses = courseRepository.findAll();
        }

        List<Enrollment> enrollments = (currentUser != null && currentUser.getRole() == User.Role.STUDENT)
                ? enrollmentRepository.findByUser(currentUser)
                : List.of();

        List<Map<String, Object>> courseList = courses.stream().map(c -> {
            boolean isEnrolled = enrollments.stream().anyMatch(e ->
                    e.getCourse().getId().equals(c.getId()) && e.getStatus() == Enrollment.Status.ACTIVE);

            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", c.getId().toString());
            map.put("title", c.getTitle());
            map.put("description", c.getDescription() != null ? c.getDescription() : "");
            map.put("price", c.getPrice() != null ? c.getPrice() : 0.0);
            map.put("status", c.getStatus() != null ? c.getStatus() : "PUBLISHED");
            map.put("tutorName", c.getTutor() != null ? c.getTutor().getName() : "Unassigned");
            map.put("enrolled", isEnrolled);
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(courseList);
    }

    @GetMapping("/{id}/public")
    public ResponseEntity<?> getPublicCourse(@PathVariable UUID id) {
        Course course = courseRepository.findById(id).orElse(null);
        if (course == null) {
            return ResponseEntity.status(404).body("Course not found");
        }

        List<Lesson> lessons = lessonRepository.findByCourseOrderByScheduledAtAsc(course);
        List<Map<String, Object>> syllabus = lessons.stream().map(l -> {
            boolean hasRecording = (l.getVideoS3Key() != null && !l.getVideoS3Key().trim().isEmpty());
            boolean isLive = (l.getZoomJoinUrl() != null && !l.getZoomJoinUrl().trim().isEmpty());
            String type = hasRecording ? "VIDEO" : (isLive ? "LIVE_CLASS" : "VIDEO");
            return Map.<String, Object>of(
                "id", l.getId().toString(),
                "title", l.getTitle(),
                "type", type,
                "hasRecording", hasRecording,
                "isLiveClass", isLive
            );
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
            "id", course.getId().toString(),
            "title", course.getTitle(),
            "description", course.getDescription() != null ? course.getDescription() : "",
            "price", course.getPrice() != null ? course.getPrice() : 0.0,
            "tutorName", course.getTutor() != null ? course.getTutor().getName() : "Unassigned",
            "syllabus", syllabus
        ));
    }

    @GetMapping("/{id}/details")
    public ResponseEntity<?> getCourseDetails(@PathVariable UUID id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        User currentUser = userRepository.findByEmail(auth.getName()).orElse(null);
        if (currentUser == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        Course course = courseRepository.findById(id).orElse(null);
        if (course == null) {
            return ResponseEntity.status(404).body("Course not found");
        }

        // Access Control: Admins can view everything.
        // Tutors can view if they are the course tutor.
        // Students can view only if actively enrolled.
        if (currentUser.getRole() == User.Role.STUDENT) {
            java.util.Optional<Enrollment> enrollment = enrollmentRepository.findByUserAndCourse(currentUser, course);
            if (enrollment.isEmpty() || enrollment.get().getStatus() != Enrollment.Status.ACTIVE) {
                return ResponseEntity.status(403).body("You are not enrolled in this course");
            }
        } else if (currentUser.getRole() == User.Role.TUTOR && course.getTutor() != null && !course.getTutor().getId().equals(currentUser.getId())) {
            return ResponseEntity.status(403).body("You are not the instructor for this course");
        }

        // Retrieve real syllabus from database
        List<Lesson> lessons = lessonRepository.findByCourseOrderByScheduledAtAsc(course);
        List<Map<String, Object>> syllabus = lessons.stream().map(l -> {
            boolean hasRecording = (l.getVideoS3Key() != null && !l.getVideoS3Key().trim().isEmpty());
            boolean isLive = (l.getZoomJoinUrl() != null && !l.getZoomJoinUrl().trim().isEmpty());
            String type = hasRecording ? "VIDEO" : (isLive ? "LIVE_CLASS" : "VIDEO");

            Map<String, Object> item = new java.util.HashMap<>();
            item.put("id", l.getId().toString());
            item.put("title", l.getTitle());
            item.put("type", type);
            item.put("hasRecording", hasRecording);
            item.put("isLiveClass", isLive);
            item.put("videoS3Key", l.getVideoS3Key());
            item.put("documentS3Key", l.getDocumentS3Key());
            item.put("zoomJoinUrl", l.getZoomJoinUrl());
            item.put("zoomMeetingId", l.getZoomMeetingId());
            item.put("scheduledAt", l.getScheduledAt() != null ? l.getScheduledAt().toString() : null);
            return item;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
            "id", course.getId().toString(),
            "title", course.getTitle(),
            "description", course.getDescription() != null ? course.getDescription() : "",
            "price", course.getPrice() != null ? course.getPrice() : 0.0,
            "tutorName", course.getTutor() != null ? course.getTutor().getName() : "Unassigned",
            "syllabus", syllabus
        ));
    }

    @PostMapping
    public ResponseEntity<?> createCourse(@RequestBody Map<String, String> request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        User currentUser = userRepository.findByEmail(auth.getName()).orElse(null);
        if (currentUser == null || (currentUser.getRole() != User.Role.ADMIN && currentUser.getRole() != User.Role.TUTOR)) {
            return ResponseEntity.status(403).body("Forbidden");
        }

        Course course = new Course();
        course.setTitle(request.get("title"));
        course.setDescription(request.get("description"));

        if (request.containsKey("price")) {
            try {
                course.setPrice(Double.parseDouble(request.get("price")));
            } catch (NumberFormatException e) {
                course.setPrice(0.0);
            }
        }

        if (currentUser.getRole() == User.Role.TUTOR) {
            course.setTutor(currentUser);
        }

        courseRepository.save(course);
        return ResponseEntity.ok(Map.of("message", "Course created successfully", "id", course.getId().toString()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCourse(@PathVariable UUID id, @RequestBody Map<String, String> request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = userRepository.findByEmail(auth.getName()).orElse(null);

        if (currentUser == null || (currentUser.getRole() != User.Role.ADMIN && currentUser.getRole() != User.Role.TUTOR)) {
            return ResponseEntity.status(403).body("Forbidden");
        }

        Course course = courseRepository.findById(id).orElse(null);
        if (course == null) {
            return ResponseEntity.status(404).body("Course not found");
        }

        if (currentUser.getRole() == User.Role.TUTOR && course.getTutor() != null && !course.getTutor().getId().equals(currentUser.getId())) {
            return ResponseEntity.status(403).body("You can only edit your own courses");
        }

        if (request.containsKey("title")) course.setTitle(request.get("title"));
        if (request.containsKey("description")) course.setDescription(request.get("description"));
        if (request.containsKey("status")) course.setStatus(request.get("status"));
        if (request.containsKey("price")) {
            try {
                course.setPrice(Double.parseDouble(request.get("price")));
            } catch (NumberFormatException ignored) {}
        }

        courseRepository.save(course);
        return ResponseEntity.ok(Map.of("message", "Course updated successfully", "id", course.getId().toString()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable UUID id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = userRepository.findByEmail(auth.getName()).orElse(null);

        if (currentUser == null || (currentUser.getRole() != User.Role.ADMIN && currentUser.getRole() != User.Role.TUTOR)) {
            return ResponseEntity.status(403).body("Forbidden");
        }

        Course course = courseRepository.findById(id).orElse(null);
        if (course == null) {
            return ResponseEntity.status(404).body("Course not found");
        }

        if (currentUser.getRole() == User.Role.TUTOR && course.getTutor() != null && !course.getTutor().getId().equals(currentUser.getId())) {
            return ResponseEntity.status(403).body("You can only delete your own courses");
        }

        courseRepository.delete(course);
        return ResponseEntity.ok(Map.of("message", "Course deleted successfully"));
    }
}
