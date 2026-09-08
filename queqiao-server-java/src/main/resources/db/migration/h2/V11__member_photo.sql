-- 会员本人照片（采集页上传，前端压缩后 base64 存库）：t_member 增加 photo 大文本字段
alter table t_member add column photo clob;
