package com.ahn.settlement.repository;

import com.ahn.settlement.dto.query.RefundAggregate;
import com.ahn.settlement.dto.query.RefundsSummary;
import com.ahn.settlement.entity.RefundRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface RefundRecordRepository extends JpaRepository<RefundRecord, String> {

    @Query("""
            SELECT COALESCE(SUM(r.refundAmount), 0)
            FROM RefundRecord r
            WHERE r.saleRecord.id = :saleRecordId
            """)
    Long sumRefundBySaleRecordId(@Param("saleRecordId") String saleRecordId);

    @Query("""
            SELECT new com.ahn.settlement.dto.query.RefundsSummary(
                COALESCE(SUM(r.refundAmount), 0), COUNT(r))
            FROM RefundRecord r
            WHERE r.saleRecord.course.creator.id = :creatorId
              AND r.canceledAt >= :start AND r.canceledAt < :end
            """)
    RefundsSummary summarizeByCreatorAndPeriod(
            @Param("creatorId") String creatorId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    @Query("""
            SELECT new com.ahn.settlement.dto.query.RefundAggregate(
                r.saleRecord.course.creator.id, r.saleRecord.course.creator.name,
                COALESCE(SUM(r.refundAmount), 0), COUNT(r))
            FROM RefundRecord r
            WHERE r.canceledAt >= :start AND r.canceledAt < :end
            GROUP BY r.saleRecord.course.creator.id, r.saleRecord.course.creator.name
            """)
    List<RefundAggregate> aggregateRefundsByCreator(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);
}
