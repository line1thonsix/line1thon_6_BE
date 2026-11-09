package com.line1thon.team6.analysis.entity;

/** 분석 작업 종류 = 사용자가 고른 길 (잠금 상태 머신 문서) */
public enum AnalysisMode {
    /** 생각을 제출함 → 비교 분석. 끝나면 UNLOCKED_SELF */
    COMPARE,
    /** 힌트 2단계 뒤 기초 학습을 고름 → 기초 설명. 끝나면 UNLOCKED_ASSISTED */
    BASIC
}
