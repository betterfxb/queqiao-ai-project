package com.queqiao.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步执行配置（P0-11）。
 *
 * <p>AI 属于外部依赖且响应不稳定，旁路型调用（成功案例草稿生成、反馈回灌等）
 * 一律走独立有界线程池，避免占满 Tomcat 请求线程；队列满时由调用线程兜底执行
 * （CallerRunsPolicy），保证任务不丢且形成天然背压。</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /** 旁路 AI / 后台任务线程池 */
    @Bean("aiExecutor")
    public ThreadPoolTaskExecutor aiExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(200);
        ex.setKeepAliveSeconds(60);
        ex.setThreadNamePrefix("ai-async-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(10);
        ex.initialize();
        return ex;
    }

    @Override
    public Executor getAsyncExecutor() {
        return aiExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.warn("[async] {} 执行失败: {}", method.getName(), ex.getMessage());
            new SimpleAsyncUncaughtExceptionHandler().handleUncaughtException(ex, method, params);
        };
    }
}
