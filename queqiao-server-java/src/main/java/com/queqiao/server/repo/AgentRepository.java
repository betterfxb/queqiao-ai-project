package com.queqiao.server.repo;

import com.queqiao.server.entity.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentRepository extends JpaRepository<Agent, String>, JpaSpecificationExecutor<Agent> {

    List<Agent> findByStatus(String status);

    Optional<Agent> findByName(String name);
}
