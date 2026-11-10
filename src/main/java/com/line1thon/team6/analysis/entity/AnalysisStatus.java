package com.line1thon.team6.analysis.entity;

/** 분석 작업 진행 상황 (응답에서는 analysisStatus) */
public enum AnalysisStatus {
    /** AI 호출 중. 프론트는 2초마다 조회 */
    PENDING,
    /** 완료. 이 순간 채팅 status가 UNLOCKED_*로 바뀜 */
    DONE,
    /** 실패. 첫 시도 포함 최대 3회까지 재시도 */
    FAILED
}
