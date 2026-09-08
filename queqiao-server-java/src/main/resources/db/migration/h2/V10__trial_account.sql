-- 体验账号额度（#290）：t_sys_user 增加「次数 + 天数」双限字段
-- 语义：is_trial=true 才受约束；trial_locked 由服务端在触顶/到期时自动置 true，需管理员重开
alter table t_sys_user add column is_trial boolean default false;
alter table t_sys_user add column trial_quota int;
alter table t_sys_user add column trial_used int default 0;
alter table t_sys_user add column trial_expire_at timestamp;
alter table t_sys_user add column trial_locked boolean default false;
