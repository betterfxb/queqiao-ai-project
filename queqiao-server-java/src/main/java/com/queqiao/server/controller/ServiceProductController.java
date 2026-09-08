package com.queqiao.server.controller;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.common.Money;
import com.queqiao.server.entity.ServiceProduct;
import com.queqiao.server.repo.ServiceProductRepository;
import com.queqiao.server.security.RequestContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 服务产品（SKU）管理。
 * 权限：主管及以上可读；仅管理员可新建 / 编辑 / 下架（删除）。
 */
@RestController
@RequestMapping("/api/products")
public class ServiceProductController {

    private final ServiceProductRepository repo;

    public ServiceProductController(ServiceProductRepository repo) {
        this.repo = repo;
    }

    /** 产品列表（主管可读） */
    @GetMapping
    public List<ServiceProduct> list(@RequestParam(required = false) String category,
                                      @RequestParam(required = false) String status) {
        RequestContext.requireManager();
        List<ServiceProduct> all = repo.findAll();
        if (category != null && !category.isBlank()) {
            all = all.stream().filter(p -> category.equals(p.getCategory())).toList();
        }
        if (status != null && !status.isBlank()) {
            all = all.stream().filter(p -> status.equals(p.getStatus())).toList();
        }
        return all;
    }

    /** 新建产品（管理员） */
    @PostMapping
    @Transactional
    public ServiceProduct create(@RequestBody ServiceProduct body) {
        RequestContext.requireAdmin();
        validate(body);
        ServiceProduct p = new ServiceProduct();
        p.setId(Ids.biz("SP"));
        p.setName(body.getName());
        p.setCategory(body.getCategory());
        p.setPrice(body.getPrice());
        p.setDurationDays(body.getDurationDays());
        p.setBenefits(body.getBenefits());
        p.setStatus(body.getStatus() == null ? "在售" : body.getStatus());
        p.setCreatedAt(LocalDateTime.now());
        return repo.save(p);
    }

    /** 编辑产品（管理员） */
    @PutMapping("/{id}")
    @Transactional
    public ServiceProduct update(@PathVariable String id, @RequestBody ServiceProduct body) {
        RequestContext.requireAdmin();
        ServiceProduct p = repo.findById(id).orElseThrow(() -> BizException.notFound("服务产品"));
        if (body.getName() != null) p.setName(body.getName());
        if (body.getCategory() != null) p.setCategory(body.getCategory());
        if (body.getPrice() != null) {
            if (!Money.gtZero(body.getPrice())) throw new BizException("价格必须大于 0");
            p.setPrice(body.getPrice());
        }
        if (body.getDurationDays() != null) {
            if (body.getDurationDays() < 0) throw new BizException("有效期不能为负");
            p.setDurationDays(body.getDurationDays());
        }
        if (body.getBenefits() != null) p.setBenefits(body.getBenefits());
        if (body.getStatus() != null) p.setStatus(body.getStatus());
        return repo.save(p);
    }

    /** 删除 / 下架产品（管理员） */
    @DeleteMapping("/{id}")
    @Transactional
    public java.util.Map<String, Object> delete(@PathVariable String id) {
        RequestContext.requireAdmin();
        ServiceProduct p = repo.findById(id).orElseThrow(() -> BizException.notFound("服务产品"));
        repo.delete(p);
        return java.util.Map.of("ok", true);
    }

    private void validate(ServiceProduct p) {
        if (p.getName() == null || p.getName().isBlank()) throw new BizException("请填写产品名称");
        if (p.getCategory() == null || p.getCategory().isBlank()) throw new BizException("请选择产品分类");
        if (p.getPrice() == null || !Money.gtZero(p.getPrice())) throw new BizException("价格必须大于 0");
        if (p.getDurationDays() != null && p.getDurationDays() < 0) throw new BizException("有效期不能为负");
    }
}
