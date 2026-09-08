package com.queqiao.server.repo;

import com.queqiao.server.entity.FinanceOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface FinanceOrderRepository extends JpaRepository<FinanceOrder, String>, JpaSpecificationExecutor<FinanceOrder> {

    List<FinanceOrder> findByStatus(String status);

    List<FinanceOrder> findByMemberIdAndStatus(String memberId, String status);

    List<FinanceOrder> findByOrderByCreatedAtDesc();

    List<FinanceOrder> findByCreatedAtAfter(LocalDateTime t);

    Optional<FinanceOrder> findByOrderNo(String orderNo);

    /** 按原订单 id 查其退款单（幂等退款时复用，返回同一笔退款记录） */
    Optional<FinanceOrder> findByOrderId(String orderId);

    /** P0-10：红娘数据范围下沉 DB，替代 findAll() 后内存过滤 */
    List<FinanceOrder> findByAgent(String agent);
}
