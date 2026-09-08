package com.queqiao.server.repo;

import com.queqiao.server.entity.Pair;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface PairRepository extends JpaRepository<Pair, String>, JpaSpecificationExecutor<Pair> {

    List<Pair> findByStatus(String status);

    List<Pair> findByMaleIdAndStatus(String maleId, String status);

    List<Pair> findByFemaleIdAndStatus(String femaleId, String status);

    List<Pair> findByAgent(String agent);

    long countByStatus(String status);

    long countByCreatedAtAfter(LocalDateTime t);

    List<Pair> findByCreatedAtAfter(LocalDateTime t);

    List<Pair> findByMaleIdOrFemaleId(String a, String b);
}
