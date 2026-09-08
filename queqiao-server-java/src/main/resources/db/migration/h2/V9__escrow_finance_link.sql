-- 阶段四 4-A 支付与交易闭环：EscrowOrder 关联原始交易订单，实现「套餐购买 → 资金托管」溯源
alter table t_escrow_order add column finance_order_id varchar(64);
