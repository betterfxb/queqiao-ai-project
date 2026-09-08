-- 3-C 反诈增强（#261）：高危风险自动建单表，附被害人救助引导文案
create table t_work_order (
  id varchar(64) primary key,
  title varchar(128),
  member_id varchar(64),
  member_name varchar(64),
  type varchar(32),
  risk_type varchar(32),
  status varchar(16),
  priority varchar(16),
  related_risk_id varchar(64),
  guidance text,
  detail text,
  assignee varchar(64),
  handler varchar(64),
  handle_note text,
  handled_at timestamp,
  created_at timestamp
);
