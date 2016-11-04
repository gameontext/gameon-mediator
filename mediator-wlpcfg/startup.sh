#!/bin/bash

# Configure amalgam8 for this container
export A8_SERVICE=mediator:v1
export A8_ENDPOINT_PORT=9443
export A8_ENDPOINT_TYPE=https

export CONTAINER_NAME=mediator

SERVER_PATH=/opt/ibm/wlp/usr/servers/defaultServer

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

  mkdir -p ${SERVER_PATH}/resources/security
  cd ${SERVER_PATH}/resources/
  etcdctl get /proxy/third-party-ssl-cert > cert.pem
  openssl pkcs12 -passin pass:keystore -passout pass:keystore -export -out cert.pkcs12 -in cert.pem
  keytool -import -v -trustcacerts -alias default -file cert.pem -storepass truststore -keypass keystore -noprompt -keystore security/truststore.jks
  keytool -genkey -storepass testOnlyKeystore -keypass wefwef -keyalg RSA -alias endeca -keystore security/key.jks -dname CN=rsssl,OU=unknown,O=unknown,L=unknown,ST=unknown,C=CA
  keytool -delete -storepass testOnlyKeystore -alias endeca -keystore security/key.jks
  keytool -v -importkeystore -srcalias 1 -alias 1 -destalias default -noprompt -srcstorepass keystore -deststorepass testOnlyKeystore -srckeypass keystore -destkeypass testOnlyKeystore -srckeystore cert.pkcs12 -srcstoretype PKCS12 -destkeystore security/key.jks -deststoretype JKS
  cd ${SERVER_PATH}

  export MAP_KEY=$(etcdctl get /passwords/map-key)
  export MAP_SERVICE_URL=$(etcdctl get /map/url)
  export PLAYER_SERVICE_URL=$(etcdctl get /player/url)
  export LOGSTASH_ENDPOINT=$(etcdctl get /logstash/endpoint)
  export LOGMET_HOST=$(etcdctl get /logmet/host)
  export LOGMET_PORT=$(etcdctl get /logmet/port)
  export LOGMET_TENANT=$(etcdctl get /logmet/tenant)
  export LOGMET_PWD=$(etcdctl get /logmet/pwd)
  export SYSTEM_ID=$(etcdctl get /global/system_id)

  GAMEON_MODE=$(etcdctl get /global/mode)
  export GAMEON_MODE=${GAMEON_MODE:-production}
  export TARGET_PLATFORM=$(etcdctl get /global/targetPlatform)

  #to run with message hub, we need a jaas jar we can only obtain
  #from github, and have to use an extra config snippet to enable it.
  wget https://github.com/ibm-messaging/message-hub-samples/raw/master/java/message-hub-liberty-sample/lib-message-hub/messagehub.login-1.0.0.jar

  exec /opt/ibm/wlp/bin/server run defaultServer
else
  exec a8sidecar --log --proxy --register --supervise /opt/ibm/wlp/bin/server run defaultServer
fi
