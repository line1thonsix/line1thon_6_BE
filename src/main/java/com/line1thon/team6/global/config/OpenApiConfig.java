package com.line1thon.team6.global.config;

import com.line1thon.team6.session.auth.CurrentSession;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Swagger UI(/swagger-ui/index.html) 설정.
 * 모든 API가 X-Guest-Token 헤더를 쓰므로, 화면의 Authorize 버튼으로 토큰을 한 번 넣으면 모든 요청에 붙게 한다.
 * 토큰은 POST /api/sessions(토큰 없이 호출)로 받는다.
 */
@Configuration
public class OpenApiConfig {

    private static final String GUEST_TOKEN = "guestToken";

    static {
        // @CurrentSession GuestSession은 서버가 X-Guest-Token으로 채우는 값이라 요청 파라미터가 아니다.
        // 숨기지 않으면 Swagger가 GuestSession 필드를 쿼리 파라미터(id, token, ...)로 펼쳐 보여 준다
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentSession.class);
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Stack Overgrow API")
                        .version("v1")
                        .description("POST /api/sessions로 받은 토큰을 Authorize에 넣은 뒤 호출하세요."))
                .components(new Components().addSecuritySchemes(GUEST_TOKEN, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-Guest-Token")))
                .addSecurityItem(new SecurityRequirement().addList(GUEST_TOKEN));
    }

    /**
     * 카테고리(태그)를 화면 흐름 순서로 정렬한다. 이름은 Notion API 명세의 카테고리이고, 각 API는 컨트롤러의 @Tag로 붙는다.
     * springdoc이 태그를 이름순으로 모으기 때문에, 문서를 다 만든 뒤 이 순서로 다시 맞춘다. 목록에 없는 태그는 뒤에 둔다.
     */
    @Bean
    public OpenApiCustomizer tagOrderCustomizer() {
        return openApi -> {
            if (openApi.getTags() == null) {
                return;
            }
            List<Tag> sorted = new ArrayList<>(openApi.getTags());
            sorted.sort(Comparator.comparingInt(tag -> {
                int index = TAG_ORDER.indexOf(tag.getName());
                return index < 0 ? TAG_ORDER.size() : index;
            }));
            openApi.setTags(sorted);
        };
    }

    private static final List<String> TAG_ORDER = List.of("세션", "채팅", "힌트·기초학습", "생각·잠금", "AI 분석");
}
