package com.ahn.settlement.repository;

import com.ahn.settlement.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, String> {}
