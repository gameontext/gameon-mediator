FROM websphere-liberty:latest

MAINTAINER Ben Smith

EXPOSE 9446

ADD https://download.elastic.co/logstash-forwarder/binaries/logstash-forwarder_linux_amd64 /opt/forwarder

ADD ./servers/gameon-mediator /opt/ibm/wlp/usr/servers/defaultServer/
RUN rm /opt/ibm/wlp/usr/servers/defaultServer/server.env

COPY ./forwarder.conf /opt/forwarder.conf

COPY ./startup.sh /opt/startup.sh

CMD ["/opt/startup.sh"]
