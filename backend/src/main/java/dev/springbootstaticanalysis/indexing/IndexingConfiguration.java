package dev.springbootstaticanalysis.indexing;

import dev.springbootstaticanalysis.shared.SpringBootStaticAnalysisProperties;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class IndexingConfiguration {

    @Bean("indexingExecutor")
    Executor indexingExecutor(SpringBootStaticAnalysisProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.indexing().maxConcurrentJobs());
        executor.setMaxPoolSize(properties.indexing().maxConcurrentJobs());
        executor.setQueueCapacity(properties.indexing().queueCapacity());
        executor.setThreadNamePrefix("spring-boot-static-analysis-index-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
