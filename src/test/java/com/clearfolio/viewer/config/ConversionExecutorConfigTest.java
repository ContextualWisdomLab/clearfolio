package com.clearfolio.viewer.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class ConversionExecutorConfigTest {

    @Test
    void conversionExecutorUsesBoundedThreadPoolSettings() {
        ConversionProperties properties = new ConversionProperties();
        properties.setWorkerThreads(0);
        properties.setQueueCapacity(0);
        ConversionExecutorConfig config = new ConversionExecutorConfig();

        Executor executor = config.conversionExecutor(properties);

        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        try {
            assertThat(taskExecutor.getCorePoolSize()).isEqualTo(1);
            assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(1);
            assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("conversion-worker-");
        } finally {
            taskExecutor.shutdown();
        }
    }
}
