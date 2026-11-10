package com.line1thon.team6.chat.entity;

/** 채팅 잠금 상태. LockService에서만 바꾼다 (잠금 상태 머신 문서) */
public enum ChatStatus {
    LOCKED,
    UNLOCKED_SELF,
    UNLOCKED_ASSISTED
}
