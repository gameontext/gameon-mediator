FROM gameontext/docker-liberty-custom:master-29

ENV SERVERDIRNAME mediator

ADD https://raw.githubusercontent.com/gameontext/gameon/master/bin/gen-keystore.sh /opt/gen-keystore.sh
USER 0
RUN chmod g+rwx /opt/gen-keystore.sh
USER 1001
COPY ./startup.sh /opt/startup.sh
COPY ./servers/gameon-mediator /opt/ol/wlp/usr/servers/defaultServer/

RUN mkdir -p /opt/ol/wlp/usr/servers/defaultServer/resources/security

CMD ["/opt/startup.sh"]

# Generous start time for constrained systems
HEALTHCHECK \
  --timeout=10s \
  --start-period=40s \
  CMD wget -q -O /dev/null  http://localhost:9080/mediator/health
