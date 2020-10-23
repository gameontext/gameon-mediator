FROM gameontext/docker-liberty-custom:master-29

ENV SERVERDIRNAME mediator

COPY ./startup.sh /opt/startup.sh
COPY ./servers/gameon-mediator /opt/ol/wlp/usr/servers/defaultServer/

RUN mkdir -p /opt/ol/wlp/usr/servers/defaultServer/resources/security

CMD ["/opt/startup.sh"]

# Generous start time for constrained systems
HEALTHCHECK \
  --timeout=10s \
  --start-period=40s \
  CMD wget -q -O /dev/null  http://localhost:9080/mediator/health
