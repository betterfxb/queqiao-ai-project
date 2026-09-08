package com.queqiao.server.repo;

import com.queqiao.server.entity.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SysUserRepository extends JpaRepository<SysUser, String>, JpaSpecificationExecutor<SysUser> {

    Optional<SysUser> findByUsername(String username);

    boolean existsByUsername(String username);

    /** 按角色过滤下沉到 DB，避免整表加载后内存过滤 */
    List<SysUser> findByRole(String role);
}
