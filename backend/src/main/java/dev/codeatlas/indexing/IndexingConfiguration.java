package dev.codeatlas.indexing;

import dev.codeatlas.shared.CodeAtlasProperties;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class IndexingConfiguration {

    @Bean("indexingExecutor")
    Executor indexingExecutor(CodeAtlasProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.indexing().maxConcurrentJobs());
        executor.setMaxPoolSize(properties.indexing().maxConcurrentJobs());
        executor.setQueueCapacity(properties.indexing().queueCapacity());
        executor.setThreadNamePrefix("code-atlas-index-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
