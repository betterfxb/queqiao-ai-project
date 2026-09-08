-- 2-B 全民红娘裂变（#247）：t_member 增加会员专属推荐码
alter table t_member add column referral_code varchar(32);
