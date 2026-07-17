package com.drivingschool.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Bounded executor for outbound-email dispatch (EmailNotificationSender,
 * EmailService's password-reset mail) - both currently run their SMTP call
 * synchronously in the request thread, so a slow/unreachable mail server adds
 * real latency to every caller. Named explicitly rather than relying on
 * @Async's default SimpleAsyncTaskExecutor, which spawns an unbounded thread
 * per call and would let a burst of requests exhaust resources under load.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("notification-");
        executor.initialize();
        return executor;
    }
}
