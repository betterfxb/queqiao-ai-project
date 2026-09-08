-- 3-D 满意度闭环（#260）：跟进记录增加幂等键，支持低分反馈自动建跟进去重
alter table t_follow_up add column dedup_key varchar(128);
