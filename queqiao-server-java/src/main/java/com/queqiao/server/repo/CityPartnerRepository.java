package com.queqiao.server.repo;

import com.queqiao.server.entity.CityPartner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface CityPartnerRepository extends JpaRepository<CityPartner, String>, JpaSpecificationExecutor<CityPartner> {

    List<CityPartner> findAllByOrderByCreatedAtDesc();

    List<CityPartner> findByCity(String city);

    List<CityPartner> findByStatusOrderByCreatedAtDesc(String status);
}
