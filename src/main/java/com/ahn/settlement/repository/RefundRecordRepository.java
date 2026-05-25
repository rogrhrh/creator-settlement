package com.ahn.settlement.repository;

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
            SELECT COALESCE(SUM(r.refundAmount), 0)
            FROM RefundRecord r
            WHERE r.saleRecord.course.creator.id = :creatorId
              AND r.canceledAt >= :start AND r.canceledAt < :end
            """)
    Long sumRefundByCreatorAndPeriod(
            @Param("creatorId") String creatorId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    @Query("""
            SELECT COUNT(r)
            FROM RefundRecord r
            WHERE r.saleRecord.course.creator.id = :creatorId
              AND r.canceledAt >= :start AND r.canceledAt < :end
            """)
    long countByCreatorAndPeriod(
            @Param("creatorId") String creatorId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    @Query("""
            SELECT r.saleRecord.course.creator.id,
                   COALESCE(SUM(r.refundAmount), 0), COUNT(r)
            FROM RefundRecord r
            WHERE r.canceledAt >= :start AND r.canceledAt < :end
            GROUP BY r.saleRecord.course.creator.id
            """)
    List<Object[]> aggregateRefundsByCreator(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);
}
