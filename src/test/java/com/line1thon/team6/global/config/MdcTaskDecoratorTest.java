package com.line1thon.team6.global.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTaskDecoratorTest {

    private final AsyncConfig.MdcTaskDecorator decorator = new AsyncConfig.MdcTaskDecorator();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void 작업을_넘길_때의_requestId를_작업_스레드에서_쓸_수_있다() throws InterruptedException {
        MDC.put("requestId", "req_1234");
        AtomicReference<String> seen = new AtomicReference<>();
        Runnable task = decorator.decorate(() -> seen.set(MDC.get("requestId")));
        MDC.clear();

        Thread worker = new Thread(task);
        worker.start();
        worker.join();

        assertThat(seen.get()).isEqualTo("req_1234");
    }

    @Test
    void 작업이_끝나면_작업_스레드의_MDC를_원래대로_되돌린다() {
        MDC.put("requestId", "req_task");
        Runnable task = decorator.decorate(() -> {
        });
        MDC.put("requestId", "req_worker");

        task.run();

        assertThat(MDC.get("requestId")).isEqualTo("req_worker");
    }
}
