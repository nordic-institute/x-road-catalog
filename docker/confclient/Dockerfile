FROM ubuntu:22.04

RUN cat > /etc/environment <<EOL
en_US.UTF-8
EOL
RUN apt update && apt install -y locales software-properties-common curl
RUN locale-gen en_US.UTF-8

RUN curl https://artifactory.niis.org/api/gpg/key/public | apt-key add -
RUN apt-add-repository -y "deb https://artifactory.niis.org/xroad-snapshot-deb $(lsb_release -sc)-snapshot main"

RUN apt update && apt install -y xroad-confclient && apt-get clean && rm -rf /var/lib/apt/lists/*

COPY confclient/data/configuration-anchor.xml /etc/xroad/configuration-anchor.xml

RUN cat > /etc/xroad/conf.d/local.ini <<EOL
[configuration-client]
proxy-configuration-backup-cron=* * * * * ? 2099
EOL

CMD /usr/share/xroad/bin/xroad-confclient
