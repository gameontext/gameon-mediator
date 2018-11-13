FROM gameontext/docker-liberty-custom:master-14

ENV SERVERDIRNAME mediator

COPY ./startup.sh /opt/startup.sh
COPY ./servers/gameon-mediator /opt/ol/wlp/usr/servers/defaultServer/

CMD ["/opt/startup.sh"]

# Generous start time for constrained systems
HEALTHCHECK \
  --timeout=10s \
  --start-period=40s \
  CMD wget -q -O /dev/null  http://localhost:9080/mediator/health
