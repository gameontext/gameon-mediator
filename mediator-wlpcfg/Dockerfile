FROM websphere-liberty:latest

MAINTAINER Ben Smith

ADD https://download.elastic.co/logstash-forwarder/binaries/logstash-forwarder_linux_amd64 /opt/forwarder

ADD ./servers/gameon-mediator /opt/ibm/wlp/usr/servers/defaultServer/

RUN wget https://github.com/coreos/etcd/releases/download/v2.2.2/etcd-v2.2.2-linux-amd64.tar.gz -q ; \
    tar xzf etcd-v2.2.2-linux-amd64.tar.gz etcd-v2.2.2-linux-amd64/etcdctl --strip-components=1 ; \
    rm etcd-v2.2.2-linux-amd64.tar.gz ; \
    mv etcdctl /usr/local/bin/etcdctl ; \

COPY ./forwarder.conf /opt/forwarder.conf

COPY ./startup.sh /opt/startup.sh

CMD ["/opt/startup.sh"]
