#!/bin/bash
export CONTAINER_NAME=mediator

SERVER_PATH=/opt/ol/wlp/usr/servers/defaultServer
ssl_path=${SERVER_PATH}/resources/security

if [ "$ETCDCTL_ENDPOINT" != "" ]; then
  echo Setting up etcd...
  echo "** Testing etcd is accessible"
  etcdctl --debug ls
  RC=$?

  while [ $RC -ne 0 ]; do
      sleep 15

      # recheck condition
      echo "** Re-testing etcd connection"
      etcdctl --debug ls
      RC=$?
  done
  echo "etcdctl returned sucessfully, continuing"

  etcdctl get /proxy/third-party-ssl-cert > ${ssl_path}/cert.pem

  export MAP_KEY=$(etcdctl get /passwords/map-key)
  export MAP_SERVICE_URL=$(etcdctl get /map/url)
  export PLAYER_SERVICE_URL=$(etcdctl get /player/url)

  export SYSTEM_ID=$(etcdctl get /global/system_id)
  GAMEON_MODE=$(etcdctl get /global/mode)
  export GAMEON_MODE=${GAMEON_MODE:-production}
  export TARGET_PLATFORM=$(etcdctl get /global/targetPlatform)

  export KAFKA_SERVICE_URL=$(etcdctl get /kafka/url)
fi
if [ -f /etc/cert/cert.pem ]; then
  cp /etc/cert/cert.pem ${ssl_path}/cert.pem
fi

# Make sure keystores are present or are generated
/opt/gen-keystore.sh ${ssl_path} ${ssl_path}

exec /opt/ol/wlp/bin/server run defaultServer
