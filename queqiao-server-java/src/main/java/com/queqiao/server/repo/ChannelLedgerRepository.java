package com.queqiao.server.repo;

import com.queqiao.server.entity.ChannelLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ChannelLedgerRepository extends JpaRepository<ChannelLedger, String> {

    List<ChannelLedger> findByOrderId(String orderId);

    List<ChannelLedger> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
}
