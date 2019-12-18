# Don't need this if docker image is used ...
create user 'hzuser'@'localhost' identified by 'hzpass';
create database if not exists BankInABox;
grant all on BankInABox.* to 'hzuser'@'%';
flush privileges;
