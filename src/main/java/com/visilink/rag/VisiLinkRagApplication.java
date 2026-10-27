package com.visilink.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * VisiLink-RAG: Resolving Deictic Ambiguity in Instructional Videos
 * Using Triggered Multimodal Retrieval-Augmented Generation.
 *
 * Enterprise Java 21 / Spring Boot 3.2 Platform.
 */
@SpringBootApplication
@EnableAsync
public class VisiLinkRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(VisiLinkRagApplication.class, args);
    }
}
