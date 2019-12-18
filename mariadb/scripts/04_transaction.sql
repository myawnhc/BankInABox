create table transaction (
  id             char(14)     not null,
  acct_number    varchar(10),
  merchant_id    varchar(8),
  amount         float,
  location       varchar(10),
  primary key (id)
);
