package io.depguard.scan;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Executes scan pipelines on a dedicated pool so that long-running scans never block request threads.
 */
@Configuration
@EnableAsync
class AsyncScanConfig {

    private static final int CORE_POOL_SIZE = 4;

    private static final int MAX_POOL_SIZE = 10;

    private static final int QUEUE_CAPACITY = 100;

    private static final int AWAIT_TERMINATION_SECONDS = 30;

    /** Name referenced by {@code @Async("scanExecutor")} — keep in sync with {@link ScanService}. */
    static final String SCAN_EXECUTOR = "scanExecutor";

    @Bean(name = SCAN_EXECUTOR)
    ThreadPoolTaskExecutor scanExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("scan-executor-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
        return executor;
    }

    /** Wraps the dependency persistence of a scan into a single transaction. */
    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
