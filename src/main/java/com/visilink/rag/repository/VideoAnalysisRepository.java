package com.visilink.rag.repository;

import com.visilink.rag.model.VideoAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoAnalysisRepository extends JpaRepository<VideoAnalysis, Long> {

    List<VideoAnalysis> findAllByOrderByCreatedAtDesc();
}
