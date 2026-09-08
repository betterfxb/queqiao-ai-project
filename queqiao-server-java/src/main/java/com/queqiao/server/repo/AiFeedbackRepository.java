package com.queqiao.server.repo;

import com.queqiao.server.entity.AiFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiFeedbackRepository extends JpaRepository<AiFeedback, Long>, JpaSpecificationExecutor<AiFeedback> {

    /** 按时间正序回灌，保证重放结果与线上一致 */
    List<AiFeedback> findBySceneOrderByIdAsc(String scene);

    List<AiFeedback> findTop200ByOrderByIdDesc();
}
