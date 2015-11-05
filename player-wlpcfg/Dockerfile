FROM websphere-liberty:latest

MAINTAINER Ben Smith

ADD ./servers/gameon-player /opt/ibm/wlp/usr/servers/defaultServer/
RUN rm /opt/ibm/wlp/usr/servers/defaultServer/server.env

RUN /opt/ibm/wlp/bin/featureManager install mongodb-2.0 --acceptLicense

COPY ./startup.sh /opt/startup.sh

CMD ["/opt/startup.sh"]