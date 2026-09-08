package com.queqiao.server.repo;

import com.queqiao.server.entity.SuccessStory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface SuccessStoryRepository extends JpaRepository<SuccessStory, String>, JpaSpecificationExecutor<SuccessStory> {

    List<SuccessStory> findAllByOrderByCreatedAtDesc();

    List<SuccessStory> findByStatusOrderByCreatedAtDesc(String status);

    List<SuccessStory> findByStatusAndPublicVisibleOrderByPublishedAtDesc(String status, Boolean publicVisible);

    Optional<SuccessStory> findFirstByPairId(String pairId);
}
