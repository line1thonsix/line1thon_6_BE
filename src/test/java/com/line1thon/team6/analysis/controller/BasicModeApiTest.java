package com.line1thon.team6.analysis.controller;

import com.line1thon.team6.ai.analysis.AnalysisResultWriter;
import com.line1thon.team6.ai.analysis.BasicExplanation;
import com.line1thon.team6.analysis.entity.AnalysisMode;
import com.line1thon.team6.analysis.entity.AnalysisStatus;
import com.line1thon.team6.analysis.repository.AnalysisJobRepository;
import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.QuestionType;
import com.line1thon.team6.chat.event.AnalysisRequested;
import com.line1thon.team6.chat.repository.ChatRepository;
import com.line1thon.team6.chat.service.ChatService;
import com.line1thon.team6.chat.service.LockService;
import com.line1thon.team6.session.entity.GuestSession;
import com.line1thon.team6.session.repository.GuestSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/chats/{chatId}/basic-mode와 기초 학습의 분석 조회 응답.
 * 테스트 설정은 분석 리스너가 꺼져 있어서, 설명 결과는 AnalysisResultWriter로 직접 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class BasicModeApiTest {

    private static final BasicExplanation EXPLANATION =
            new BasicExplanation("요약", "const [users, setUsers] = useState([]);", "관찰", "변경");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    GuestSessionRepository guestSessionRepository;

    @Autowired
    ChatRepository chatRepository;

    @Autowired
    ChatService chatService;

    @Autowired
    LockService lockService;

    @Autowired
    AnalysisResultWriter writer;

    @Autowired
    AnalysisJobRepository analysisJobRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    ApplicationEvents events;

    GuestSession session;
    String token;

    @BeforeEach
    void setUp() {
        token = UUID.randomUUID().toString();
        session = guestSessionRepository.save(new GuestSession(token));
    }

    private Long doneChat(GuestSession owner, int hintLevel) {
        Long chatId = chatRepository.save(Chat.create(owner, UUID.randomUUID().toString(), QuestionType.CONCEPT,
                null, "클로저가 뭔가요?", null, false)).getId();
        chatService.completePrepare(chatId, List.of("칩1", "칩2", "칩3"), List.of("힌트1", "힌트2"), "기준 답변 전문");
        jdbcTemplate.update("update chat set hint_level = ? where id = ?", hintLevel, chatId);
        return chatId;
    }

    private ResultActions basicMode(Long chatId) throws Exception {
        return mockMvc.perform(post("/api/chats/{chatId}/basic-mode", chatId).header("X-Guest-Token", token));
    }

    private Long jobIdOf(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("jobId").asLong();
    }

    @Test
    void 힌트_2단계를_봤으면_202와_BASIC_작업을_만들고_분석을_요청한다() throws Exception {
        Long chatId = doneChat(session, 2);

        ResultActions result = basicMode(chatId)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNumber())
                .andExpect(jsonPath("$.mode").value("BASIC"))
                .andExpect(jsonPath("$.analysisStatus").value("PENDING"))
                .andExpect(jsonPath("$.status").value("LOCKED"));

        Long jobId = jobIdOf(result);
        assertThat(analysisJobRepository.findById(jobId).orElseThrow().getMode()).isEqualTo(AnalysisMode.BASIC);
        assertThat(events.stream(AnalysisRequested.class)).contains(new AnalysisRequested(chatId, jobId));
    }

    @Test
    void 다시_눌러도_200으로_같은_작업을_돌려준다() throws Exception {
        Long chatId = doneChat(session, 2);
        Long jobId = jobIdOf(basicMode(chatId));

        basicMode(chatId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId));
        assertThat(analysisJobRepository.count()).isPositive();
        assertThat(analysisJobRepository.findByChatId(chatId)).get().extracting(j -> j.getId()).isEqualTo(jobId);
    }

    @Test
    void 힌트를_2단계까지_안_봤으면_400_BASIC_MODE_NOT_ALLOWED() throws Exception {
        for (int hintLevel : new int[]{0, 1}) {
            Long chatId = doneChat(session, hintLevel);

            basicMode(chatId)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BASIC_MODE_NOT_ALLOWED"));
            assertThat(analysisJobRepository.findByChatId(chatId)).isEmpty();
        }
    }

    @Test
    void 생각을_이미_제출했으면_409_ALREADY_SUBMITTED() throws Exception {
        Long chatId = doneChat(session, 2);
        lockService.startJob(session, chatId, AnalysisMode.COMPARE);

        basicMode(chatId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_SUBMITTED"));
    }

    @Test
    void 기초_학습을_시작한_뒤에는_생각_제출이_409_ALREADY_SUBMITTED() throws Exception {
        Long chatId = doneChat(session, 2);
        basicMode(chatId).andExpect(status().isAccepted());

        mockMvc.perform(post("/api/chats/{chatId}/thoughts", chatId)
                        .header("X-Guest-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"첫 렌더링 때 users 값부터 볼게요. 데이터가 오기 전에는 undefined라서요.\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_SUBMITTED"));
    }

    @Test
    void 남의_대화면_404_CHAT_NOT_FOUND이고_토큰이_없으면_401() throws Exception {
        GuestSession other = guestSessionRepository.save(new GuestSession(UUID.randomUUID().toString()));
        Long othersChat = doneChat(other, 2);

        basicMode(othersChat)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_NOT_FOUND"));
        mockMvc.perform(post("/api/chats/{chatId}/basic-mode", othersChat))
                .andExpect(status().isUnauthorized());
        assertThat(analysisJobRepository.findByChatId(othersChat)).isEmpty();
    }

    @Test
    void 설명이_만들어지면_분석_조회에서_네_칸_객체와_ASSISTED를_준다() throws Exception {
        Long chatId = doneChat(session, 2);
        Long jobId = jobIdOf(basicMode(chatId));
        writer.completeBasic(chatId, jobId, EXPLANATION);

        mockMvc.perform(get("/api/chats/{chatId}/analysis", chatId).header("X-Guest-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisStatus").value("DONE"))
                .andExpect(jsonPath("$.mode").value("BASIC"))
                .andExpect(jsonPath("$.unlockType").value("ASSISTED"))
                .andExpect(jsonPath("$.myThought").isEmpty())
                .andExpect(jsonPath("$.referenceAnswer").value("기준 답변 전문"))
                .andExpect(jsonPath("$.findings.length()").value(0))
                .andExpect(jsonPath("$.basicExplanation.summary").value("요약"))
                .andExpect(jsonPath("$.basicExplanation.exampleCode").value("const [users, setUsers] = useState([]);"))
                .andExpect(jsonPath("$.basicExplanation.observe").value("관찰"))
                .andExpect(jsonPath("$.basicExplanation.change").value("변경"));
        assertThat(chatRepository.findById(chatId).orElseThrow().getStatus().name()).isEqualTo("UNLOCKED_ASSISTED");
    }

    @Test
    void 설명을_만드는_중이면_결과를_가린다() throws Exception {
        Long chatId = doneChat(session, 2);
        basicMode(chatId);

        mockMvc.perform(get("/api/chats/{chatId}/analysis", chatId).header("X-Guest-Token", token))
                .andExpect(jsonPath("$.analysisStatus").value(AnalysisStatus.PENDING.name()))
                .andExpect(jsonPath("$.unlockType").isEmpty())
                .andExpect(jsonPath("$.basicExplanation").isEmpty())
                .andExpect(jsonPath("$.referenceAnswer").isEmpty());
    }
}
