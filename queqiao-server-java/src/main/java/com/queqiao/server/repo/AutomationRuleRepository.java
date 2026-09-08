package com.queqiao.server.repo;

import com.queqiao.server.entity.AutomationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AutomationRuleRepository extends JpaRepository<AutomationRule, String> {
}
