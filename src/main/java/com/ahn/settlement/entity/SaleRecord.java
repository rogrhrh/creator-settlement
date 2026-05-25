package com.ahn.settlement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "sale_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleRecord {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false)
    private String studentId;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private OffsetDateTime paidAt;

    public SaleRecord(String id, Course course, String studentId, Long amount, OffsetDateTime paidAt) {
        this.id = id;
        this.course = course;
        this.studentId = studentId;
        this.amount = amount;
        this.paidAt = paidAt;
    }
}
