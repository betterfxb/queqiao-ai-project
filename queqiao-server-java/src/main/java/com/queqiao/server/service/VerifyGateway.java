package com.queqiao.server.service;

import com.queqiao.server.entity.VerifyTask;

/**
 * 第三方核验适配层：屏蔽学信网/实名公安/材料审核等渠道差异。
 * 当前实时接口密钥待配置，统一走「人工核验留痕」通道；后续接入真实 API 时
 * 仅需新增一个 VerifyGateway 实现并在 VerifyService 中按 channel 选择即可。
 */
public interface VerifyGateway {

    /** 提交一次核验，返回渠道侧初始状态与说明 */
    VerifyResult submit(VerifyTask task);

    record VerifyResult(String status, String note) {
    }
}
