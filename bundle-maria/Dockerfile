FROM library/mariadb:latest

# Same as in BankInABox.properties
ENV MYSQL_DATABASE "BankInABox"

# Not exactly secure
ENV MYSQL_ROOT_PASSWORD "root"

# Scripts to set up user and tables, then load data from backup

COPY target/classes/01_users.sql   docker-entrypoint-initdb.d/
COPY target/classes/02_account.sql  docker-entrypoint-initdb.d/
COPY target/classes/03_merchant.sql  docker-entrypoint-initdb.d/
COPY target/classes/04_transaction.sql  docker-entrypoint-initdb.d/
COPY target/classes/05_dataload.sh   docker-entrypoint-initdb.d/
COPY target/classes/bib_dump.sql  data/backup/bib_dump.sql