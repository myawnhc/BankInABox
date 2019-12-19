# Create user with full access to BankInABox database
create user 'hzuser'@'%' identified by 'hzpass';
create database if not exists BankInABox;
grant all on BankInABox.* to 'hzuser'@'%';
flush privileges;
