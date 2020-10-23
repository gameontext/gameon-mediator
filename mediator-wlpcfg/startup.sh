#!/bin/bash
export CONTAINER_NAME=mediator

SERVER_PATH=/opt/ol/wlp/usr/servers/defaultServer

certpath=/tmp/java-ssl/
mkdir -p ${certpath}

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

  etcdctl get /proxy/third-party-ssl-cert > ${certpath}/cert.pem

  export MAP_KEY=$(etcdctl get /passwords/map-key)
  export MAP_SERVICE_URL=$(etcdctl get /map/url)
  export PLAYER_SERVICE_URL=$(etcdctl get /player/url)

  export SYSTEM_ID=$(etcdctl get /global/system_id)
  GAMEON_MODE=$(etcdctl get /global/mode)
  export GAMEON_MODE=${GAMEON_MODE:-production}
  export TARGET_PLATFORM=$(etcdctl get /global/targetPlatform)

  export KAFKA_SERVICE_URL=$(etcdctl get /kafka/url)
fi

if [ -f ${certpath}/cert.pem ]; then
  echo "Building keystore/truststore from cert.pem"
  echo "-creating dir"
  mkdir -p ${SERVER_PATH}/resources/security
  echo "-cd dir"
  cd ${SERVER_PATH}/resources/
  echo "-importing jvm truststore to server truststore"
  keytool -importkeystore -srckeystore $JAVA_HOME/jre/lib/security/cacerts -destkeystore security/truststore.jks -srcstorepass changeit -deststorepass truststore
  echo "-converting pem to pkcs12"
  openssl pkcs12 -passin pass:keystore -passout pass:keystore -export -out cert.pkcs12 -in ${certpath}/cert.pem
  echo "-importing pem to truststore.jks"
  keytool -import -v -trustcacerts -alias default -file ${certpath}/cert.pem -storepass truststore -keypass keystore -noprompt -keystore security/truststore.jks
  echo "-creating dummy key.jks"
  keytool -genkey -storepass testOnlyKeystore -keypass wefwef -keyalg RSA -alias endeca -keystore security/key.jks -dname CN=rsssl,OU=unknown,O=unknown,L=unknown,ST=unknown,C=CA
  echo "-emptying key.jks"
  keytool -delete -storepass testOnlyKeystore -alias endeca -keystore security/key.jks
  echo "-importing pkcs12 to key.jks"
  keytool -v -importkeystore -srcalias 1 -alias 1 -destalias default -noprompt -srcstorepass keystore -deststorepass testOnlyKeystore -srckeypass keystore -destkeypass testOnlyKeystore -srckeystore cert.pkcs12 -srcstoretype PKCS12 -destkeystore security/key.jks -deststoretype JKS
  echo "done"
  cd ${SERVER_PATH}
fi

exec /opt/ol/wlp/bin/server run defaultServer
