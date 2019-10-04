FROM library/mariadb:latest

# Same as in BankInABox.properties
ENV MYSQL_DATABASE "BankInABox"

# Not exactly secure
ENV MYSQL_ROOT_PASSWORD "root"

# Table set up
#COPY target/classes/account.sql        docker-entrypoint-initdb.d/
#COPY target/classes/merchant.sql       docker-entrypoint-initdb.d/
#COPY target/classes/transaction.sql    docker-entrypoint-initdb.d/
