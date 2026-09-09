package com.lms.backend.repository;

import com.lms.backend.entity.Course;
import com.lms.backend.entity.Payment;
import com.lms.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByUserOrderByCreatedAtDesc(User user);
    List<Payment> findByCourse(Course course);
}
