package com.queqiao.server.repo;

import com.queqiao.server.entity.SettingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SettingItemRepository extends JpaRepository<SettingItem, String>, JpaSpecificationExecutor<SettingItem> {

    List<SettingItem> findByCategory(String category);
}
