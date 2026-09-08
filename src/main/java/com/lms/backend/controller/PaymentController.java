package com.lms.backend.controller;

import com.lms.backend.entity.Course;
import com.lms.backend.entity.Enrollment;
import com.lms.backend.entity.User;
import com.lms.backend.repository.CourseRepository;
import com.lms.backend.repository.EnrollmentRepository;
import com.lms.backend.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;

    public PaymentController(UserRepository userRepository, CourseRepository courseRepository, EnrollmentRepository enrollmentRepository) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(@RequestBody Map<String, String> request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User currentUser = userRepository.findByEmail(email).orElse(null);

        if (currentUser == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        String courseIdStr = request.get("courseId");
        if (courseIdStr == null) {
            return ResponseEntity.badRequest().body("Course ID is required");
        }

        UUID courseId;
        try {
            courseId = UUID.fromString(courseIdStr);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid Course ID format");
        }

        Course course = courseRepository.findById(courseId).orElse(null);
        if (course == null) {
            return ResponseEntity.status(404).body("Course not found");
        }

        // Simulate PayHere API Delay
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Simulate 10% failure rate for realism
        if (Math.random() < 0.1) {
            return ResponseEntity.status(402).body("Payment Declined by Issuer");
        }

        // Success! Create or update Enrollment
        Optional<Enrollment> existingOpt = enrollmentRepository.findByUserAndCourse(currentUser, course);
        Enrollment enrollment;
        if (existingOpt.isPresent()) {
            enrollment = existingOpt.get();
        } else {
            enrollment = new Enrollment();
            enrollment.setUser(currentUser);
            enrollment.setCourse(course);
        }
        
        enrollment.setStatus(Enrollment.Status.ACTIVE);
        enrollmentRepository.save(enrollment);

        return ResponseEntity.ok(Map.of(
            "message", "Payment Successful",
            "courseTitle", course.getTitle(),
            "transactionId", "PH_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
        ));
    }
}
