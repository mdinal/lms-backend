package com.lms.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
    }

    @Test
    void testTokenGenerationAndExtraction() {
        String email = "student@example.com";
        String role = "STUDENT";

        String token = jwtUtil.generateToken(email, role);
        assertNotNull(token);

        assertEquals(email, jwtUtil.extractEmail(token));
        assertEquals(role, jwtUtil.extractRole(token));
        assertTrue(jwtUtil.validateToken(token, email));
    }

    @Test
    void testInvalidUserEmailFailsValidation() {
        String token = jwtUtil.generateToken("student@example.com", "STUDENT");
        assertFalse(jwtUtil.validateToken(token, "other@example.com"));
    }
}
