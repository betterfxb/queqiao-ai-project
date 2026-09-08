-- P0-8/P0-7（MySQL）：金额列由浮点改为 decimal(18,2)，与实体 BigDecimal 对齐，杜绝精度丢失；
-- 同时为活动报名补唯一约束，配合服务层事务实现「同一会员同一活动只能报名一次」。
alter table t_activity modify column price decimal(18,2);
alter table t_activity modify column revenue decimal(18,2);
alter table t_channel_ledger modify column amount decimal(18,2);
alter table t_commission_record modify column amount decimal(18,2);
alter table t_commission_record modify column order_amount decimal(18,2);
alter table t_community_tier modify column price decimal(18,2);
alter table t_coupon modify column amount decimal(18,2);
alter table t_finance_order modify column amount decimal(18,2);
alter table t_finance_order modify column coupon_amount decimal(18,2);
alter table t_merchant_order modify column amount decimal(18,2);
alter table t_merchant_order modify column commission_amount decimal(18,2);
alter table t_pay_reconcile modify column local_amount decimal(18,2);
alter table t_pay_reconcile modify column channel_amount decimal(18,2);
alter table t_pay_reconcile modify column diff decimal(18,2);
alter table t_referral modify column reward_amount decimal(18,2);
alter table t_referral modify column order_amount decimal(18,2);
alter table t_service_product modify column price decimal(18,2);

alter table t_activity_signup add constraint uk_signup_activity_member unique (activity_id, member_id);
