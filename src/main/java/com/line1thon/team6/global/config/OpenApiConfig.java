package com.line1thon.team6.global.config;

import com.line1thon.team6.session.auth.CurrentSession;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
                        .title("Deveil API")
                        .version("v1")
                        .description("POST /api/sessions로 받은 토큰을 Authorize에 넣은 뒤 호출하세요."))
                .components(new Components().addSecuritySchemes(GUEST_TOKEN, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-Guest-Token")))
                .addSecurityItem(new SecurityRequirement().addList(GUEST_TOKEN));
    }
}
