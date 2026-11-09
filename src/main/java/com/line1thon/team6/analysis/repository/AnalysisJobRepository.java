package com.line1thon.team6.analysis.repository;

import com.line1thon.team6.analysis.entity.AnalysisJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {

    /** 채팅의 분석 작업 (채팅당 최대 1개) */
    Optional<AnalysisJob> findByChatId(Long chatId);
}
