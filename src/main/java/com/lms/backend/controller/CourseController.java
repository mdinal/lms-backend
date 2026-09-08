package com.lms.backend.controller;

import com.lms.backend.entity.Course;
import com.lms.backend.entity.User;
import com.lms.backend.repository.CourseRepository;
import com.lms.backend.repository.UserRepository;
import com.lms.backend.repository.EnrollmentRepository;
import com.lms.backend.entity.Enrollment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;

    public CourseController(CourseRepository courseRepository, UserRepository userRepository, EnrollmentRepository enrollmentRepository) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    @GetMapping
    public ResponseEntity<?> getCourses() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User currentUser = userRepository.findByEmail(email).orElse(null);

        if (currentUser == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        List<Course> courses;
        
        // Admins see all courses, Tutors see only their courses, Students see all active courses
        if (currentUser.getRole() == User.Role.TUTOR) {
            courses = courseRepository.findByTutor(currentUser);
        } else {
            courses = courseRepository.findAll();
        }

        // Fetch enrollments if user is a student
        List<Enrollment> enrollments = (currentUser.getRole() == User.Role.STUDENT) 
            ? enrollmentRepository.findByUser(currentUser) 
            : List.of();

        // Map to avoid lazy loading issues with tutor and lessons
        List<Map<String, Object>> courseList = courses.stream().map(c -> {
            boolean isEnrolled = enrollments.stream().anyMatch(e -> e.getCourse().getId().equals(c.getId()) && e.getStatus() == Enrollment.Status.ACTIVE);
            
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", c.getId().toString());
            map.put("title", c.getTitle());
            map.put("description", c.getDescription() != null ? c.getDescription() : "");
            map.put("price", c.getPrice() != null ? c.getPrice() : 0.0);
            map.put("tutorName", c.getTutor() != null ? c.getTutor().getName() : "Unassigned");
            map.put("enrolled", isEnrolled);
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(courseList);
    }

    @GetMapping("/{id}/details")
    public ResponseEntity<?> getCourseDetails(@PathVariable java.util.UUID id) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User currentUser = userRepository.findByEmail(email).orElse(null);

        if (currentUser == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        Course course = courseRepository.findById(id).orElse(null);
        if (course == null) {
            return ResponseEntity.status(404).body("Course not found");
        }

        // Access Control
        if (currentUser.getRole() == User.Role.STUDENT) {
            java.util.Optional<Enrollment> enrollment = enrollmentRepository.findByUserAndCourse(currentUser, course);
            if (enrollment.isEmpty() || enrollment.get().getStatus() != Enrollment.Status.ACTIVE) {
                return ResponseEntity.status(403).body("You are not enrolled in this course");
            }
        } else if (currentUser.getRole() == User.Role.TUTOR && !course.getTutor().getId().equals(currentUser.getId())) {
            return ResponseEntity.status(403).body("You are not the instructor for this course");
        }

        // Build Syllabus (Mocking lessons since we haven't fully populated them yet)
        // In a real scenario, this would be: course.getLessons().stream().map(...)
        List<Map<String, Object>> mockSyllabus = List.of(
            Map.of("id", "lesson_1", "title", "1. Introduction to the Course", "type", "VIDEO", "duration", "15:00"),
            Map.of("id", "lesson_2", "title", "2. Core Concepts & Terminology", "type", "VIDEO", "duration", "45:30"),
            Map.of("id", "lesson_3", "title", "3. Weekly Live Q&A Session", "type", "LIVE_CLASS", "duration", "60:00")
        );

        return ResponseEntity.ok(Map.of(
            "id", course.getId().toString(),
            "title", course.getTitle(),
            "description", course.getDescription() != null ? course.getDescription() : "",
            "tutorName", course.getTutor() != null ? course.getTutor().getName() : "Unassigned",
            "syllabus", mockSyllabus
        ));
    }

    @PostMapping
    public ResponseEntity<?> createCourse(@RequestBody Map<String, String> request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User currentUser = userRepository.findByEmail(email).orElse(null);

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
        
        // Assign to the current user if they are a tutor, or allow admin to assign later (null for now)
        if (currentUser.getRole() == User.Role.TUTOR) {
            course.setTutor(currentUser);
        }

        courseRepository.save(course);
        return ResponseEntity.ok(Map.of("message", "Course created successfully", "id", course.getId().toString()));
    }
}
