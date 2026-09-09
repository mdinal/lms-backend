package com.lms.backend.controller;

import com.lms.backend.entity.User;
import com.lms.backend.repository.UserRepository;
import com.lms.backend.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(userRepository, passwordEncoder, jwtUtil);
    }

    @Test
    void testRegisterEnforcesStudentRole() {
        when(userRepository.findByEmail("newstudent@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hashedPassword");

        // Attempting to register as ADMIN via request payload
        Map<String, String> request = Map.of(
            "name", "Test Student",
            "email", "newstudent@example.com",
            "password", "password123",
            "role", "ADMIN"
        );

        ResponseEntity<?> response = authController.register(request);
        assertEquals(200, response.getStatusCode().value());

        // Verify that the saved user has STUDENT role regardless of input
        verify(userRepository).save(argThat(user -> user.getRole() == User.Role.STUDENT));
    }

    @Test
    void testRegisterRejectsDuplicateEmail() {
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(new User()));

        Map<String, String> request = Map.of(
            "name", "Existing User",
            "email", "existing@example.com",
            "password", "password123"
        );

        ResponseEntity<?> response = authController.register(request);
        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().toString().contains("already registered"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void testLoginSuccessReturnsTokenAndDetails() {
        User user = new User();
        user.setName("John Doe");
        user.setEmail("john@example.com");
        user.setPasswordHash("hashedPassword");
        user.setRole(User.Role.STUDENT);

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashedPassword")).thenReturn(true);
        when(jwtUtil.generateToken("john@example.com", "STUDENT")).thenReturn("mock-jwt-token");

        Map<String, String> request = Map.of(
            "email", "john@example.com",
            "password", "password123"
        );

        ResponseEntity<?> response = authController.login(request);
        assertEquals(200, response.getStatusCode().value());

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertEquals("mock-jwt-token", body.get("token"));
        assertEquals("STUDENT", body.get("role"));
        assertEquals("John Doe", body.get("name"));
    }
}
