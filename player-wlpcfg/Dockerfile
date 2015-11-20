FROM websphere-liberty:latest

MAINTAINER Ben Smith

ADD https://download.elastic.co/logstash-forwarder/binaries/logstash-forwarder_linux_amd64 /opt/forwarder
ADD https://admin:PLACEHOLDER_ADMIN_PASSWORD@game-on.org:8443/logstashneeds.tar /opt/logstashneeds.tar

ADD ./servers/gameon-player /opt/ibm/wlp/usr/servers/defaultServer/
RUN rm /opt/ibm/wlp/usr/servers/defaultServer/server.env ; \
 	/opt/ibm/wlp/bin/featureManager install mongodb-2.0 --acceptLicense ; \
 	cd /opt ; chmod +x ./forwarder ; tar xvzf logstashneeds.tar ; rm logstashneeds.tar
 	
COPY ./forwarder.conf /opt/forwarder.conf 	

COPY ./startup.sh /opt/startup.sh

CMD ["/opt/startup.sh"]