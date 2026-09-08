-- 2-C 家长参与模式（#250）：家长-子女授权关系表
create table t_member_guardian (
  id varchar(64) primary key,
  member_id varchar(64),
  member_name varchar(64),
  guardian_phone varchar(32),
  guardian_name varchar(64),
  guardian_member_id varchar(64),
  relation varchar(16),
  auth_status varchar(16),
  role varchar(16),
  remark text,
  authorized_at timestamp,
  created_at timestamp,
  updated_at timestamp
);
