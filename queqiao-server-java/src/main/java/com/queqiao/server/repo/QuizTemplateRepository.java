package com.queqiao.server.repo;

import com.queqiao.server.entity.QuizTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuizTemplateRepository extends JpaRepository<QuizTemplate, String> {
}
