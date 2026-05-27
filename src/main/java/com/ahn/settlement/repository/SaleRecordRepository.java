package com.ahn.settlement.repository;

import com.ahn.settlement.dto.query.SaleAggregate;
import com.ahn.settlement.dto.query.SalesSummary;
import com.ahn.settlement.entity.SaleRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface SaleRecordRepository extends JpaRepository<SaleRecord, String> {

    @Query("""
            SELECT new com.ahn.settlement.dto.query.SalesSummary(
                COALESCE(SUM(s.amount), 0), COUNT(s))
            FROM SaleRecord s
            WHERE s.course.creator.id = :creatorId
              AND s.paidAt >= :start AND s.paidAt < :end
            """)
    SalesSummary summarizeByCreatorAndPeriod(
            @Param("creatorId") String creatorId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    @Query("""
            SELECT s FROM SaleRecord s JOIN FETCH s.course
            WHERE s.course.creator.id = :creatorId
            ORDER BY s.paidAt
            """)
    List<SaleRecord> findByCreatorId(@Param("creatorId") String creatorId);

    @Query("""
            SELECT s FROM SaleRecord s JOIN FETCH s.course
            WHERE s.course.creator.id = :creatorId
              AND s.paidAt >= :start AND s.paidAt < :end
            ORDER BY s.paidAt
            """)
    List<SaleRecord> findByCreatorIdAndPeriod(
            @Param("creatorId") String creatorId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    @Query("""
            SELECT new com.ahn.settlement.dto.query.SaleAggregate(
                s.course.creator.id, s.course.creator.name,
                COALESCE(SUM(s.amount), 0), COUNT(s))
            FROM SaleRecord s
            WHERE s.paidAt >= :start AND s.paidAt < :end
            GROUP BY s.course.creator.id, s.course.creator.name
            """)
    List<SaleAggregate> aggregateSalesByCreator(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);
}
