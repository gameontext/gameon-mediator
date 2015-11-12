#!/bin/bash
/opt/ibm/wlp/bin/server start defaultServer
echo Running Logstash Forwarder...
sed -i s/PLACEHOLDER_LOGHOST/$LOGGING_DOCKER_HOST/g /opt/forwarder.conf
cd /opt ; ./forwarder --config ./forwarder.conf
