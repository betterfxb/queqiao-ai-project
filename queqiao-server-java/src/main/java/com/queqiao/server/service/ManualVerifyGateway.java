package com.queqiao.server.service;

import com.queqiao.server.entity.VerifyTask;
import org.springframework.stereotype.Service;

/**
 * 人工核验通道（默认实现）：实时接口密钥待配置，所有核验先进入「待核验」留痕，
 * 由运营在审核页录入核验结果。学信网/实名公安等实时渠道接入后作为独立实现替换。
 */
@Service
public class ManualVerifyGateway implements VerifyGateway {

    @Override
    public VerifyResult submit(VerifyTask task) {
        String channel = task.getChannel() == null ? "" : task.getChannel();
        if (channel.contains("学信网") || channel.contains("公安") || channel.contains("实名")) {
            return new VerifyResult("待核验",
                    "第三方实时接口密钥待配置，已转为人工核验留痕，请运营在审核页录入结果");
        }
        return new VerifyResult("待核验", "人工核验留痕，请运营在审核页录入核验结果");
    }
}
