package com.lms.backend.repository;

import com.lms.backend.entity.Course;
import com.lms.backend.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, UUID> {
    List<Lesson> findByCourse(Course course);
    List<Lesson> findByCourseOrderByScheduledAtAsc(Course course);
}
