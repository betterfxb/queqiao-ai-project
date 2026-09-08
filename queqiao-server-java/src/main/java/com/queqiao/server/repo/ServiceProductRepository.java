package com.queqiao.server.repo;

import com.queqiao.server.entity.ServiceProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceProductRepository extends JpaRepository<ServiceProduct, String> {
}
