FROM websphere-liberty:beta

MAINTAINER Erin Schnabel <schnabel@us.ibm.com> (@ebullientworks)

RUN wget https://github.com/coreos/etcd/releases/download/v2.2.2/etcd-v2.2.2-linux-amd64.tar.gz -q && \
    tar xzf etcd-v2.2.2-linux-amd64.tar.gz etcd-v2.2.2-linux-amd64/etcdctl --strip-components=1 && \
    rm etcd-v2.2.2-linux-amd64.tar.gz && \
    mv etcdctl /usr/local/bin/etcdctl

# Install required features
RUN /opt/ibm/wlp/bin/installUtility install  --acceptLicense \
    apiDiscovery-1.0 \
    bluemixLogCollector-1.1 \
    cdi-1.2 \
    concurrent-1.0 \
    couchdb-1.0 \
    localConnector-1.0 \
    jaxrs-2.0 \
    jndi-1.0 \
    jsonp-1.0 \
    ssl-1.0 \
    websocket-1.1

RUN wget -qO- https://github.com/amalgam8/amalgam8/releases/download/v0.3.0/a8sidecar.sh | sh

ENV SERVERDIRNAME mediator

COPY ./startup.sh /opt/startup.sh
ADD ./servers/gameon-mediator /opt/ibm/wlp/usr/servers/defaultServer/

CMD ["/opt/startup.sh"]
