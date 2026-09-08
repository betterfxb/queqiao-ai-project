-- 3-D 预付资金存管（#260）：高客单价套餐预付款进入存管，履约放款、争议退款，全程状态留痕（退费透明）
create table t_escrow_order (
  id varchar(64) primary key,
  member_id varchar(64),
  member_name varchar(64),
  package_id varchar(64),
  package_name varchar(128),
  amount bigint,
  currency varchar(16),
  channel varchar(32),
  status varchar(16),
  escrow_no varchar(64),
  paid_at timestamp,
  hold_at timestamp,
  released_at timestamp,
  refund_amount bigint,
  refund_reason text,
  refund_at timestamp,
  created_at timestamp,
  updated_at timestamp
);
