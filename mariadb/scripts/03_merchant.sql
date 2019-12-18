create table merchant (
  id             char(8)     not null,
  name           varchar(32),
  reputation     smallint,
  avg_txn_amount float,
  location       varchar(10),
  primary key (id)
);
