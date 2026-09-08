-- 2-A 实人认证徽章（#244）：t_member 增加实人认证标记与时间戳
alter table t_member add column verify_real_person boolean default false;
alter table t_member add column verify_real_person_at timestamp;
