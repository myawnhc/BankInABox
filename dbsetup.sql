drop database if exists BankInABoxDB;

create database BankInABoxDB;

use BankInABoxDB;

create table merchant (
      id               char(8)       not null,
      name             varchar(32),
      reputation       smallint,         // range 1-10
      avg_txn_amount   float,
      location         varchar(10),   // geohash
      primary key ( id ),
);