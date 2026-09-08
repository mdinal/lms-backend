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

        User user = userRepository.findByEmail(email).orElse(null);

        if (user != null && passwordEncoder.matches(password, user.getPasswordHash())) {
            String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
            return ResponseEntity.ok(Map.of("token", token, "role", user.getRole().name()));
        }

        return ResponseEntity.status(401).body("Invalid credentials");
    }

    // Temporary registration for testing
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        User user = new User();
        user.setName(request.get("name"));
        user.setEmail(request.get("email"));
        user.setPasswordHash(passwordEncoder.encode(request.get("password")));
        user.setRole(User.Role.valueOf(request.getOrDefault("role", "STUDENT")));
        
        userRepository.save(user);
        return ResponseEntity.ok("User registered successfully");
    }
}
