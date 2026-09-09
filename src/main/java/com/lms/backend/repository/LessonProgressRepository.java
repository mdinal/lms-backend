package com.lms.backend.repository;

import com.lms.backend.entity.Course;
import com.lms.backend.entity.Lesson;
import com.lms.backend.entity.LessonProgress;
import com.lms.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LessonProgressRepository extends JpaRepository<LessonProgress, UUID> {
    Optional<LessonProgress> findByUserAndLesson(User user, Lesson lesson);
    List<LessonProgress> findByUserAndLesson_Course(User user, Course course);
}
