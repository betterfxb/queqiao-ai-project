package com.queqiao.server.pay;

import com.queqiao.server.entity.FinanceOrder;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 线下支付网关：门店收款，红娘/财务确认后标记支付。
 * 始终可用，工程闭环可在无第三方密钥时完整跑通。
 */
@Component
public class OfflineGateway implements PayGateway {

    @Override
    public String channel() {
        return "线下";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public PrepayResult prepay(FinanceOrder order) {
        PrepayResult r = new PrepayResult();
        r.offline = true;
        r.channel = "线下";
        r.params = Map.of("hint", "线下收款：由红娘/财务确认后标记支付");
        return r;
    }

    @Override
    public boolean verifyNotify(Map<String, String> params, String sign) {
        return true; // 线下信任门店，无需验签
    }
}
