package com.lms.backend.controller;

import com.lms.backend.entity.User;
import com.lms.backend.repository.UserRepository;
import com.lms.backend.security.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");

        if (email == null || password == null) {
            return ResponseEntity.badRequest().body("Email and password are required");
        }

        User user = userRepository.findByEmail(email).orElse(null);

        if (user != null && passwordEncoder.matches(password, user.getPasswordHash())) {
            String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("token", token);
            response.put("id", user.getId() != null ? user.getId().toString() : "");
            response.put("name", user.getName());
            response.put("email", user.getEmail());
            response.put("role", user.getRole().name());
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(401).body("Invalid credentials");
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        String email = request.get("email");
        String password = request.get("password");

        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Name is required");
        }
        if (email == null || email.trim().isEmpty() || !email.contains("@")) {
            return ResponseEntity.badRequest().body("Valid email is required");
        }
        if (password == null || password.length() < 6) {
            return ResponseEntity.badRequest().body("Password must be at least 6 characters");
        }

        if (userRepository.findByEmail(email.trim().toLowerCase()).isPresent()) {
            return ResponseEntity.badRequest().body("Email is already registered");
        }

        User user = new User();
        user.setName(name.trim());
        user.setEmail(email.trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(password));
        // Enforce STUDENT role for public registration to prevent unauthorized elevation
        user.setRole(User.Role.STUDENT);
        
        userRepository.save(user);
        return ResponseEntity.ok(Map.of(
            "message", "User registered successfully",
            "id", user.getId() != null ? user.getId().toString() : ""
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        String email = auth.getName();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body("User not found");
        }

        return ResponseEntity.ok(Map.of(
            "id", user.getId().toString(),
            "name", user.getName(),
            "email", user.getEmail(),
            "role", user.getRole().name()
        ));
    }
}
