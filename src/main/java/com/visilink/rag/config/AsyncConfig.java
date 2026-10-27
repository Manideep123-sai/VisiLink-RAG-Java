package com.visilink.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Concurrency Architecture Configuration:
 * Section 4.3 of VisiLink-RAG paper specifies a fixed thread pool of 4 worker threads
 * to execute non-blocking parallel frame interpretation pipelines.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "frameAnalysisExecutor")
    public ExecutorService frameAnalysisExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}
