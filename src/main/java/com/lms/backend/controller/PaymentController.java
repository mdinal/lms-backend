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
@RequestMapping("/api/payments")
public class PaymentController {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PaymentRepository paymentRepository;

    public PaymentController(UserRepository userRepository, CourseRepository courseRepository,
                             EnrollmentRepository enrollmentRepository, PaymentRepository paymentRepository) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.paymentRepository = paymentRepository;
    }

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(@RequestBody Map<String, String> request) {
        User currentUser = getAuthenticatedUser();
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

        // Generate Transaction Reference
        String transactionRef = "PAY_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Record Payment
        Payment payment = new Payment();
        payment.setUser(currentUser);
        payment.setCourse(course);
        payment.setAmount(course.getPrice() != null ? course.getPrice() : 0.0);
        payment.setCurrency("USD");
        payment.setGateway("PAYHERE");
        payment.setTransactionRef(transactionRef);
        payment.setStatus(Payment.Status.COMPLETED);
        paymentRepository.save(payment);

        // Create or activate Enrollment
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
            "transactionId", transactionRef,
            "amount", payment.getAmount()
        ));
    }

    @GetMapping("/my-history")
    public ResponseEntity<?> getMyPaymentHistory() {
        User currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        List<Payment> payments = paymentRepository.findByUserOrderByCreatedAtDesc(currentUser);
        List<Map<String, Object>> history = payments.stream().map(p -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getId().toString());
            map.put("courseTitle", p.getCourse().getTitle());
            map.put("courseId", p.getCourse().getId().toString());
            map.put("amount", p.getAmount());
            map.put("currency", p.getCurrency());
            map.put("transactionRef", p.getTransactionRef());
            map.put("status", p.getStatus().name());
            map.put("createdAt", p.getCreatedAt().toString());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(history);
    }

    @GetMapping("/admin/all")
    public ResponseEntity<?> getAllPayments() {
        User currentUser = getAuthenticatedUser();
        if (currentUser == null || currentUser.getRole() != User.Role.ADMIN) {
            return ResponseEntity.status(403).body("Forbidden");
        }

        List<Payment> payments = paymentRepository.findAll();
        List<Map<String, Object>> result = payments.stream().map(p -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getId().toString());
            map.put("studentName", p.getUser().getName());
            map.put("studentEmail", p.getUser().getEmail());
            map.put("courseTitle", p.getCourse().getTitle());
            map.put("amount", p.getAmount());
            map.put("currency", p.getCurrency());
            map.put("transactionRef", p.getTransactionRef());
            map.put("status", p.getStatus().name());
            map.put("createdAt", p.getCreatedAt().toString());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> handlePaymentWebhook(@RequestBody Map<String, Object> payload) {
        // Handle external IPN callbacks from PayHere / Stripe
        return ResponseEntity.ok(Map.of("status", "received"));
    }
}
