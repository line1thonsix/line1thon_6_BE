package com.line1thon.team6.global.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;

/**
 * AI 작업을 API 응답 뒤 백그라운드에서 실행한다.
 * 요청 스레드의 MDC(requestId)를 작업 스레드로 넘겨, 같은 요청의 로그와 ai_usage_log를 이어 볼 수 있게 한다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String AI_EXECUTOR = "aiExecutor";

    @Bean(name = AI_EXECUTOR)
    public ThreadPoolTaskExecutor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // AI 호출은 대부분 응답을 기다리는 시간이라 스레드 수보다 제공사 사용량 한도가 먼저 걸린다
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        // 종료할 때 진행 중인 AI 호출(최대 15초 × 재호출)이 끝나기를 기다린다
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /** 작업을 넘길 때의 MDC를 작업 스레드에 복사하고, 끝나면 지운다 (스레드 재사용 시 섞이지 않게) */
    static class MdcTaskDecorator implements TaskDecorator {

        @Override
        public Runnable decorate(Runnable runnable) {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (context != null) {
                    MDC.setContextMap(context);
                } else {
                    MDC.clear();
                }
                try {
                    runnable.run();
                } finally {
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    } else {
                        MDC.clear();
                    }
                }
            };
        }
    }
}
