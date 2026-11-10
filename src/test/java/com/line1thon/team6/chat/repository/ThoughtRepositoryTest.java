package com.line1thon.team6.chat.repository;

import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.QuestionType;
import com.line1thon.team6.chat.entity.Thought;
import com.line1thon.team6.session.entity.GuestSession;
import com.line1thon.team6.session.repository.GuestSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** thought 테이블: 채팅당 1개, 채팅을 지우면 같이 지워짐 */
@SpringBootTest
class ThoughtRepositoryTest {

    private static final String CONTENT = "첫 렌더링 때 users 값부터 볼게요. 데이터가 오기 전에는 undefined라서요.";

    @Autowired
    GuestSessionRepository guestSessionRepository;

    @Autowired
    ChatRepository chatRepository;

    @Autowired
    ThoughtRepository thoughtRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Chat chat;

    @BeforeEach
    void setUp() {
        GuestSession session = guestSessionRepository.save(new GuestSession(UUID.randomUUID().toString()));
        chat = chatRepository.save(Chat.create(session, UUID.randomUUID().toString(), QuestionType.DEBUGGING,
                null, "질문", null, false));
    }

    @Test
    void 저장하면_내용과_칩_번호를_그대로_남긴다() {
        Thought saved = thoughtRepository.save(Thought.create(chat, CONTENT, 1));

        Thought found = thoughtRepository.findByChat(chat).orElseThrow();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getContent()).isEqualTo(CONTENT);
        assertThat(found.getSelectedChoice()).isEqualTo(1);
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void 힌트를_안_봤으면_hintUsed는_false() {
        assertThat(Thought.create(chat, CONTENT, null).isHintUsed()).isFalse();
    }

    @Test
    void 힌트를_봤으면_hintUsed는_true() {
        jdbcTemplate.update("update chat set hint_level = 1 where id = ?", chat.getId());
        Chat hinted = chatRepository.findById(chat.getId()).orElseThrow();

        assertThat(Thought.create(hinted, CONTENT, null).isHintUsed()).isTrue();
    }

    @Test
    void 같은_채팅에_두_번째_생각은_저장되지_않는다() {
        thoughtRepository.saveAndFlush(Thought.create(chat, CONTENT, null));

        assertThatThrownBy(() -> thoughtRepository.saveAndFlush(Thought.create(chat, CONTENT + " 다시", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 채팅을_지우면_생각도_지워진다() {
        Long thoughtId = thoughtRepository.save(Thought.create(chat, CONTENT, null)).getId();

        jdbcTemplate.update("delete from chat where id = ?", chat.getId());

        assertThat(thoughtRepository.findById(thoughtId)).isEmpty();
    }
}
