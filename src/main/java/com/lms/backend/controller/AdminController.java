package com.lms.backend.controller;

import com.lms.backend.entity.Course;
import com.lms.backend.entity.Enrollment;
import com.lms.backend.entity.Payment;
import com.lms.backend.entity.User;
import com.lms.backend.repository.CourseRepository;
import com.lms.backend.repository.EnrollmentRepository;
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

    public AdminController(UserRepository userRepository, CourseRepository courseRepository,
                           EnrollmentRepository enrollmentRepository, PaymentRepository paymentRepository) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.paymentRepository = paymentRepository;
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
            List<String> courseTitles = enrollments.stream()
                    .filter(e -> e.getStatus() == Enrollment.Status.ACTIVE)
                    .map(e -> e.getCourse().getTitle())
                    .collect(Collectors.toList());

            Map<String, Object> map = new HashMap<>();
            map.put("id", student.getId().toString());
            map.put("name", student.getName());
            map.put("email", student.getEmail());
            map.put("courses", courseTitles);
            map.put("enrollmentsCount", courseTitles.size());
            result.add(map);
        }

        return ResponseEntity.ok(result);
    }
}
