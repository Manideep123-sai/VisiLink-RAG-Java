package com.visilink.rag.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.io.File;

/**
 * Global Application Configuration.
 */
@Configuration
public class AppConfig {

    @Value("${visilink.rag.video-storage-dir:data/videos}")
    private String videoStorageDir;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Bean
    public CommandLineRunner initStorage() {
        return args -> {
            File dir = new File(videoStorageDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
        };
    }
}
