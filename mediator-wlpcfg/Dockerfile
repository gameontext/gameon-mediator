FROM gameontext/docker-liberty-custom:master-12

ENV SERVERDIRNAME mediator

COPY ./startup.sh /opt/startup.sh
COPY ./servers/gameon-mediator /opt/ol/wlp/usr/servers/defaultServer/
COPY ./lib/features/ /opt/ol/wlp/usr/

CMD ["/opt/startup.sh"]

# Generous start time for constrained systems
HEALTHCHECK \
  --timeout=10s \
  --start-period=40s \
  CMD wget -q -O /dev/null  http://localhost:9080/mediator/
