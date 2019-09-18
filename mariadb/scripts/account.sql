create table ACCOUNT (
  acct_number    char(10)     not null,
  credit_limit   float,
  balance        float,
  acct_status    smallint,
  location       varchar(10),
  primary key (acct_number)
);