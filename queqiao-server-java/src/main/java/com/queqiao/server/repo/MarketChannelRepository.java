package com.queqiao.server.repo;

import com.queqiao.server.entity.MarketChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface MarketChannelRepository extends JpaRepository<MarketChannel, String>, JpaSpecificationExecutor<MarketChannel> {

    List<MarketChannel> findAllByOrderByCreatedAtDesc();

    List<MarketChannel> findByStatusOrderByCreatedAtDesc(String status);

    Optional<MarketChannel> findByName(String name);

    Optional<MarketChannel> findByCode(String code);
}
