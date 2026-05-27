package com.ahn.settlement.repository;

import com.ahn.settlement.entity.SettlementRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementRecordRepository extends JpaRepository<SettlementRecord, String> {
    List<SettlementRecord> findByCreatorIdOrderByYearMonthDesc(String creatorId);
}
