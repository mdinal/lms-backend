package com.lms.backend.controller;

import com.lms.backend.entity.Course;
import com.lms.backend.entity.Enrollment;
import com.lms.backend.entity.Lesson;
import com.lms.backend.entity.LessonProgress;
import com.lms.backend.entity.User;
import com.lms.backend.repository.CourseRepository;
import com.lms.backend.repository.EnrollmentRepository;
import com.lms.backend.repository.LessonProgressRepository;
import com.lms.backend.repository.LessonRepository;
import com.lms.backend.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final LessonProgressRepository progressRepository;
    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;

    public ProgressController(LessonProgressRepository progressRepository, LessonRepository lessonRepository,
                              CourseRepository courseRepository, UserRepository userRepository,
                              EnrollmentRepository enrollmentRepository) {
        this.progressRepository = progressRepository;
        this.lessonRepository = lessonRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    @PostMapping("/lesson/{lessonId}")
    public ResponseEntity<?> updateLessonProgress(@PathVariable UUID lessonId, @RequestBody Map<String, Object> request) {
        User currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        Lesson lesson = lessonRepository.findById(lessonId).orElse(null);
        if (lesson == null) {
            return ResponseEntity.status(404).body("Lesson not found");
        }

        LessonProgress progress = progressRepository.findByUserAndLesson(currentUser, lesson)
                .orElseGet(() -> {
                    LessonProgress lp = new LessonProgress();
                    lp.setUser(currentUser);
                    lp.setLesson(lesson);
                    return lp;
                });

        if (request.containsKey("isCompleted")) {
            progress.setIsCompleted(Boolean.parseBoolean(request.get("isCompleted").toString()));
        }
        if (request.containsKey("lastPositionSeconds")) {
            progress.setLastPositionSeconds(Integer.parseInt(request.get("lastPositionSeconds").toString()));
        }

        progressRepository.save(progress);
        return ResponseEntity.ok(Map.of(
            "message", "Progress updated",
            "isCompleted", progress.getIsCompleted(),
            "lastPositionSeconds", progress.getLastPositionSeconds()
        ));
    }

    @GetMapping("/course/{courseId}")
    public ResponseEntity<?> getCourseProgress(@PathVariable UUID courseId) {
        User currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        Course course = courseRepository.findById(courseId).orElse(null);
        if (course == null) {
            return ResponseEntity.status(404).body("Course not found");
        }

        List<Lesson> lessons = lessonRepository.findByCourse(course);
        List<LessonProgress> progressList = progressRepository.findByUserAndLesson_Course(currentUser, course);

        Set<UUID> completedLessonIds = progressList.stream()
                .filter(LessonProgress::getIsCompleted)
                .map(lp -> lp.getLesson().getId())
                .collect(Collectors.toSet());

        int total = lessons.size();
        int completed = (int) lessons.stream().filter(l -> completedLessonIds.contains(l.getId())).count();
        double percentage = total > 0 ? ((double) completed / total) * 100.0 : 0.0;

        Map<String, Object> lessonProgressMap = new HashMap<>();
        for (LessonProgress lp : progressList) {
            lessonProgressMap.put(lp.getLesson().getId().toString(), Map.of(
                "isCompleted", lp.getIsCompleted() != null && lp.getIsCompleted(),
                "lastPositionSeconds", lp.getLastPositionSeconds() != null ? lp.getLastPositionSeconds() : 0,
                "updatedAt", lp.getUpdatedAt() != null ? lp.getUpdatedAt().toString() : ""
            ));
        }

        return ResponseEntity.ok(Map.of(
            "courseId", courseId.toString(),
            "totalLessons", total,
            "completedLessons", completed,
            "progressPercentage", Math.round(percentage),
            "completedLessonIds", completedLessonIds.stream().map(UUID::toString).collect(Collectors.toList()),
            "lessonProgress", lessonProgressMap
        ));
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllProgress() {
        User currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        List<Enrollment> enrollments = enrollmentRepository.findByUser(currentUser);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Enrollment enrollment : enrollments) {
            if (enrollment.getStatus() != Enrollment.Status.ACTIVE) continue;
            Course course = enrollment.getCourse();
            List<Lesson> lessons = lessonRepository.findByCourse(course);
            List<LessonProgress> progressList = progressRepository.findByUserAndLesson_Course(currentUser, course);

            Set<UUID> completedLessonIds = progressList.stream()
                    .filter(LessonProgress::getIsCompleted)
                    .map(lp -> lp.getLesson().getId())
                    .collect(Collectors.toSet());

            int total = lessons.size();
            int completed = (int) lessons.stream().filter(l -> completedLessonIds.contains(l.getId())).count();
            double percentage = total > 0 ? ((double) completed / total) * 100.0 : 0.0;

            result.add(Map.of(
                "courseId", course.getId().toString(),
                "courseTitle", course.getTitle(),
                "totalLessons", total,
                "completedLessons", completed,
                "progressPercentage", Math.round(percentage),
                "completedLessonIds", completedLessonIds.stream().map(UUID::toString).collect(Collectors.toList())
            ));
        }

        return ResponseEntity.ok(result);
    }
}
