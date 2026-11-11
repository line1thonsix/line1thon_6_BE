package com.line1thon.team6.chat.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatListResponseTest {

    @Test
    void 짧은_질문은_그대로() {
        assertThat(ChatListResponse.preview("짧은 질문")).isEqualTo("짧은 질문");
    }

    @Test
    void 정확히_40자면_자르지_않는다() {
        String question = "가".repeat(40);
        assertThat(ChatListResponse.preview(question)).isEqualTo(question);
    }

    @Test
    void 이모지가_40번째_칸에서_잘리면_그_이모지는_뺀다() {
        // 39자 + 이모지(2칸) → 40번째 칸이 이모지 앞쪽 반이라 39자만 남긴다
        String question = "가".repeat(39) + "😀" + "나나나";
        assertThat(ChatListResponse.preview(question)).isEqualTo("가".repeat(39));
    }
}
