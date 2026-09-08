-- P0-8/P0-7（H2）：金额列由浮点改为 decimal(18,2)，与实体 BigDecimal 对齐，杜绝精度丢失；
-- 同时为活动报名补唯一约束，配合服务层事务实现「同一会员同一活动只能报名一次」。
alter table t_activity alter column price decimal(18,2);
alter table t_activity alter column revenue decimal(18,2);
alter table t_channel_ledger alter column amount decimal(18,2);
alter table t_commission_record alter column amount decimal(18,2);
alter table t_commission_record alter column order_amount decimal(18,2);
alter table t_community_tier alter column price decimal(18,2);
alter table t_coupon alter column amount decimal(18,2);
alter table t_finance_order alter column amount decimal(18,2);
alter table t_finance_order alter column coupon_amount decimal(18,2);
alter table t_merchant_order alter column amount decimal(18,2);
alter table t_merchant_order alter column commission_amount decimal(18,2);
alter table t_pay_reconcile alter column local_amount decimal(18,2);
alter table t_pay_reconcile alter column channel_amount decimal(18,2);
alter table t_pay_reconcile alter column diff decimal(18,2);
alter table t_referral alter column reward_amount decimal(18,2);
alter table t_referral alter column order_amount decimal(18,2);
alter table t_service_product alter column price decimal(18,2);

alter table t_activity_signup add constraint uk_signup_activity_member unique (activity_id, member_id);
