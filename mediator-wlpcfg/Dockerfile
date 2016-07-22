FROM websphere-liberty:latest

MAINTAINER Erin Schnabel <schnabel@us.ibm.com> (@ebullientworks)

RUN wget -qO- https://github.com/amalgam8/sidecar/releases/download/v0.2.0-alpha2/install-a8sidecar.sh | sh
#RUN wget -qO- https://raw.githubusercontent.com/amalgam8/sidecar/master/install-a8sidecar.sh | sh

ADD https://download.elastic.co/logstash-forwarder/binaries/logstash-forwarder_linux_amd64 /opt/forwarder

RUN wget https://github.com/coreos/etcd/releases/download/v2.2.2/etcd-v2.2.2-linux-amd64.tar.gz -q ; \
    tar xzf etcd-v2.2.2-linux-amd64.tar.gz etcd-v2.2.2-linux-amd64/etcdctl --strip-components=1 ; \
    rm etcd-v2.2.2-linux-amd64.tar.gz ; \
    mv etcdctl /usr/local/bin/etcdctl

COPY ./forwarder.conf /opt/forwarder.conf
COPY ./startup.sh /opt/startup.sh

ADD ./servers/gameon-mediator /opt/ibm/wlp/usr/servers/defaultServer/

CMD ["/opt/startup.sh"]
