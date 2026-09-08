package com.queqiao.server.repo;

import com.queqiao.server.entity.EscrowOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EscrowRepository extends JpaRepository<EscrowOrder, String> {

    List<EscrowOrder> findByMemberIdOrderByCreatedAtDesc(String memberId);

    List<EscrowOrder> findByStatusOrderByCreatedAtDesc(String status);

    List<EscrowOrder> findAllByOrderByCreatedAtDesc();

    List<EscrowOrder> findByFinanceOrderIdOrderByCreatedAtDesc(String financeOrderId);

    boolean existsByFinanceOrderId(String financeOrderId);

    long countByStatus(String status);

    /** 按状态汇总托管金额（分），无记录返回 0 */
    @Query("select coalesce(sum(o.amount), 0) from EscrowOrder o where o.status = ?1")
    long sumAmountByStatus(String status);
}
