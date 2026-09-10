package com.lms.backend.controller;

import com.lms.backend.entity.Course;
import com.lms.backend.entity.Enrollment;
import com.lms.backend.entity.Lesson;
import com.lms.backend.entity.LessonProgress;
import com.lms.backend.entity.Payment;
import com.lms.backend.entity.User;
import com.lms.backend.repository.CourseRepository;
import com.lms.backend.repository.EnrollmentRepository;
import com.lms.backend.repository.LessonProgressRepository;
import com.lms.backend.repository.LessonRepository;
import com.lms.backend.repository.PaymentRepository;
import com.lms.backend.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PaymentRepository paymentRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository progressRepository;

    public AdminController(UserRepository userRepository, CourseRepository courseRepository,
                           EnrollmentRepository enrollmentRepository, PaymentRepository paymentRepository,
                           LessonRepository lessonRepository, LessonProgressRepository progressRepository) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.paymentRepository = paymentRepository;
        this.lessonRepository = lessonRepository;
        this.progressRepository = progressRepository;
    }

    private User getAuthenticatedAdminOrTutor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        User user = userRepository.findByEmail(auth.getName()).orElse(null);
        if (user == null || (user.getRole() != User.Role.ADMIN && user.getRole() != User.Role.TUTOR)) {
            return null;
        }
        return user;
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getAdminStats() {
        User user = getAuthenticatedAdminOrTutor();
        if (user == null) {
            return ResponseEntity.status(403).body("Forbidden");
        }

        long coursesCount = courseRepository.count();
        List<User> allUsers = userRepository.findAll();
        long studentsCount = allUsers.stream().filter(u -> u.getRole() == User.Role.STUDENT).count();

        List<Payment> payments = paymentRepository.findAll();
        double totalRevenue = payments.stream()
                .filter(p -> p.getStatus() == Payment.Status.COMPLETED)
                .mapToDouble(Payment::getAmount)
                .sum();

        return ResponseEntity.ok(Map.of(
            "courses", coursesCount,
            "students", studentsCount,
            "revenue", Math.round(totalRevenue * 100.0) / 100.0
        ));
    }

    @GetMapping("/students")
    public ResponseEntity<?> getStudents() {
        User admin = getAuthenticatedAdminOrTutor();
        if (admin == null) {
            return ResponseEntity.status(403).body("Forbidden");
        }

        List<User> students = userRepository.findAll().stream()
                .filter(u -> u.getRole() == User.Role.STUDENT)
                .collect(Collectors.toList());

        List<Map<String, Object>> result = new ArrayList<>();
        for (User student : students) {
            List<Enrollment> enrollments = enrollmentRepository.findByUser(student);
            List<String> courseTitles = new ArrayList<>();
            List<Map<String, Object>> courseProgressList = new ArrayList<>();

            int totalCompletedLessons = 0;
            int totalLessonsAllCourses = 0;

            for (Enrollment e : enrollments) {
                if (e.getStatus() != Enrollment.Status.ACTIVE) continue;
                Course course = e.getCourse();
                courseTitles.add(course.getTitle());

                List<Lesson> lessons = lessonRepository.findByCourse(course);
                List<LessonProgress> progressList = progressRepository.findByUserAndLesson_Course(student, course);

                long completedCount = progressList.stream()
                        .filter(lp -> Boolean.TRUE.equals(lp.getIsCompleted()))
                        .count();

                int total = lessons.size();
                double pct = total > 0 ? ((double) completedCount / total) * 100.0 : 0.0;

                totalCompletedLessons += completedCount;
                totalLessonsAllCourses += total;

                Map<String, Object> cp = new HashMap<>();
                cp.put("courseId", course.getId().toString());
                cp.put("courseTitle", course.getTitle());
                cp.put("totalLessons", total);
                cp.put("completedLessons", completedCount);
                cp.put("progressPercentage", Math.round(pct));
                courseProgressList.add(cp);
            }

            double overallPct = totalLessonsAllCourses > 0 
                ? ((double) totalCompletedLessons / totalLessonsAllCourses) * 100.0 
                : 0.0;

            Map<String, Object> map = new HashMap<>();
            map.put("id", student.getId().toString());
            map.put("name", student.getName());
            map.put("email", student.getEmail());
            map.put("courses", courseTitles);
            map.put("enrollmentsCount", courseTitles.size());
            map.put("overallProgressPercentage", Math.round(overallPct));
            map.put("courseProgress", courseProgressList);
            result.add(map);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/students/{id}/progress")
    public ResponseEntity<?> getStudentProgressDetail(@PathVariable UUID id) {
        User admin = getAuthenticatedAdminOrTutor();
        if (admin == null) {
            return ResponseEntity.status(403).body("Forbidden");
        }

        User student = userRepository.findById(id).orElse(null);
        if (student == null) {
            return ResponseEntity.status(404).body("Student not found");
        }

        List<Enrollment> enrollments = enrollmentRepository.findByUser(student);
        List<Map<String, Object>> coursesBreakdown = new ArrayList<>();

        for (Enrollment enrollment : enrollments) {
            if (enrollment.getStatus() != Enrollment.Status.ACTIVE) continue;
            Course course = enrollment.getCourse();
            List<Lesson> lessons = lessonRepository.findByCourseOrderByScheduledAtAsc(course);
            List<LessonProgress> progressList = progressRepository.findByUserAndLesson_Course(student, course);

            Map<UUID, LessonProgress> progressMap = progressList.stream()
                    .collect(Collectors.toMap(lp -> lp.getLesson().getId(), lp -> lp, (p1, p2) -> p1));

            List<Map<String, Object>> lessonDetails = new ArrayList<>();
            int completedCount = 0;

            for (Lesson lesson : lessons) {
                LessonProgress lp = progressMap.get(lesson.getId());
                boolean isCompleted = lp != null && Boolean.TRUE.equals(lp.getIsCompleted());
                if (isCompleted) completedCount++;

                boolean isLive = (lesson.getZoomJoinUrl() != null && !lesson.getZoomJoinUrl().isBlank());
                boolean hasRecording = (lesson.getVideoS3Key() != null && !lesson.getVideoS3Key().isBlank());

                Map<String, Object> lMap = new HashMap<>();
                lMap.put("lessonId", lesson.getId().toString());
                lMap.put("title", lesson.getTitle());
                lMap.put("isCompleted", isCompleted);
                lMap.put("lastPositionSeconds", (lp != null && lp.getLastPositionSeconds() != null) ? lp.getLastPositionSeconds() : 0);
                lMap.put("updatedAt", (lp != null && lp.getUpdatedAt() != null) ? lp.getUpdatedAt().toString() : null);
                lMap.put("isLiveClass", isLive);
                lMap.put("hasRecording", hasRecording);
                lMap.put("scheduledAt", lesson.getScheduledAt() != null ? lesson.getScheduledAt().toString() : null);
                lessonDetails.add(lMap);
            }

            int total = lessons.size();
            double pct = total > 0 ? ((double) completedCount / total) * 100.0 : 0.0;

            Map<String, Object> cMap = new HashMap<>();
            cMap.put("courseId", course.getId().toString());
            cMap.put("courseTitle", course.getTitle());
            cMap.put("enrolledAt", enrollment.getEnrolledAt() != null ? enrollment.getEnrolledAt().toString() : null);
            cMap.put("totalLessons", total);
            cMap.put("completedLessons", completedCount);
            cMap.put("progressPercentage", Math.round(pct));
            cMap.put("lessons", lessonDetails);
            coursesBreakdown.add(cMap);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", student.getId().toString());
        response.put("name", student.getName());
        response.put("email", student.getEmail());
        response.put("role", student.getRole().toString());
        response.put("enrolledCoursesCount", coursesBreakdown.size());
        response.put("courses", coursesBreakdown);

        return ResponseEntity.ok(response);
    }
}
