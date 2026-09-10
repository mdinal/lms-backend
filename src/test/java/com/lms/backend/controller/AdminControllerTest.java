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
import com.lms.backend.repository.PaymentRepository;
import com.lms.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private LessonProgressRepository progressRepository;

    private AdminController adminController;
    private User adminUser;
    private User studentUser;

    @BeforeEach
    void setUp() {
        adminController = new AdminController(
                userRepository, courseRepository, enrollmentRepository, paymentRepository,
                lessonRepository, progressRepository
        );

        adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setEmail("admin@example.com");
        adminUser.setName("Admin User");
        adminUser.setRole(User.Role.ADMIN);

        studentUser = new User();
        studentUser.setId(UUID.randomUUID());
        studentUser.setEmail("student@example.com");
        studentUser.setName("Jane Doe");
        studentUser.setRole(User.Role.STUDENT);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@example.com", "password", List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testGetStudentsReturnsProgressSummary() {
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(userRepository.findAll()).thenReturn(List.of(adminUser, studentUser));

        Course course = new Course();
        course.setId(UUID.randomUUID());
        course.setTitle("A-Level Mathematics");

        Enrollment enrollment = new Enrollment();
        enrollment.setId(UUID.randomUUID());
        enrollment.setUser(studentUser);
        enrollment.setCourse(course);
        enrollment.setStatus(Enrollment.Status.ACTIVE);

        when(enrollmentRepository.findByUser(studentUser)).thenReturn(List.of(enrollment));

        Lesson lesson1 = new Lesson();
        lesson1.setId(UUID.randomUUID());
        lesson1.setTitle("Calculus 1");

        Lesson lesson2 = new Lesson();
        lesson2.setId(UUID.randomUUID());
        lesson2.setTitle("Calculus 2");

        when(lessonRepository.findByCourse(course)).thenReturn(List.of(lesson1, lesson2));

        LessonProgress progress1 = new LessonProgress();
        progress1.setUser(studentUser);
        progress1.setLesson(lesson1);
        progress1.setIsCompleted(true);

        when(progressRepository.findByUserAndLesson_Course(studentUser, course)).thenReturn(List.of(progress1));

        ResponseEntity<?> response = adminController.getStudents();
        assertEquals(200, response.getStatusCode().value());

        List<?> students = (List<?>) response.getBody();
        assertNotNull(students);
        assertEquals(1, students.size());

        Map<?, ?> studentMap = (Map<?, ?>) students.get(0);
        assertEquals("Jane Doe", studentMap.get("name"));
        assertEquals(50L, studentMap.get("overallProgressPercentage"));

        List<?> courseProgress = (List<?>) studentMap.get("courseProgress");
        assertEquals(1, courseProgress.size());
        Map<?, ?> cpMap = (Map<?, ?>) courseProgress.get(0);
        assertEquals("A-Level Mathematics", cpMap.get("courseTitle"));
        assertEquals(50L, cpMap.get("progressPercentage"));
        assertEquals(1L, cpMap.get("completedLessons"));
        assertEquals(2, cpMap.get("totalLessons"));
    }

    @Test
    void testGetStudentProgressDetailReturnsLessonBreakdown() {
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(studentUser.getId())).thenReturn(Optional.of(studentUser));

        Course course = new Course();
        course.setId(UUID.randomUUID());
        course.setTitle("Theoretical Physics");

        Enrollment enrollment = new Enrollment();
        enrollment.setId(UUID.randomUUID());
        enrollment.setUser(studentUser);
        enrollment.setCourse(course);
        enrollment.setStatus(Enrollment.Status.ACTIVE);
        enrollment.setEnrolledAt(LocalDateTime.now());

        when(enrollmentRepository.findByUser(studentUser)).thenReturn(List.of(enrollment));

        Lesson lesson = new Lesson();
        lesson.setId(UUID.randomUUID());
        lesson.setTitle("Quantum Mechanics 101");
        lesson.setVideoS3Key("videos/qm101.m3u8");

        when(lessonRepository.findByCourseOrderByScheduledAtAsc(course)).thenReturn(List.of(lesson));

        LessonProgress progress = new LessonProgress();
        progress.setUser(studentUser);
        progress.setLesson(lesson);
        progress.setIsCompleted(true);
        progress.setLastPositionSeconds(450);

        when(progressRepository.findByUserAndLesson_Course(studentUser, course)).thenReturn(List.of(progress));

        ResponseEntity<?> response = adminController.getStudentProgressDetail(studentUser.getId());
        assertEquals(200, response.getStatusCode().value());

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertEquals("Jane Doe", body.get("name"));

        List<?> courses = (List<?>) body.get("courses");
        assertEquals(1, courses.size());

        Map<?, ?> courseMap = (Map<?, ?>) courses.get(0);
        assertEquals("Theoretical Physics", courseMap.get("courseTitle"));
        assertEquals(100L, courseMap.get("progressPercentage"));

        List<?> lessons = (List<?>) courseMap.get("lessons");
        assertEquals(1, lessons.size());
        Map<?, ?> lessonMap = (Map<?, ?>) lessons.get(0);
        assertEquals("Quantum Mechanics 101", lessonMap.get("title"));
        assertEquals(true, lessonMap.get("isCompleted"));
        assertEquals(450, lessonMap.get("lastPositionSeconds"));
        assertEquals(true, lessonMap.get("hasRecording"));
    }
}
