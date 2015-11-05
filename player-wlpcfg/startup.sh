#!/bin/bash
export DOCKERHOST=$(route -n | grep 'UG[ \t]' | awk '{print $2}')
echo Found Docker host: $DOCKERHOST
export MONGO_HOST=$DOCKERHOST
export CONCIERGE_URL=http:\/\/$DOCKERHOST:9081/concierge
/opt/ibm/wlp/bin/server run defaultServer

