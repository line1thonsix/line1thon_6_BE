package com.line1thon.team6.ai.repository;

import com.line1thon.team6.ai.entity.AiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    List<AiUsageLog> findByRequestIdOrderByIdAsc(String requestId);
}
