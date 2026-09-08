package com.queqiao.server.repo;

import com.queqiao.server.entity.DocTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocTemplateRepository extends JpaRepository<DocTemplate, String>, JpaSpecificationExecutor<DocTemplate> {

    Optional<DocTemplate> findByType(String type);

    List<DocTemplate> findAllByOrderByTypeAsc();
}
