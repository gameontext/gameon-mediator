#!/bin/bash

if [ "$SSL_CERT" != "" ]; then
  echo Found an SSL cert to use.
  cd /opt/ibm/wlp/usr/servers/defaultServer/resources/
  echo -e $SSL_CERT > cert.pem
  openssl pkcs12 -passin pass:keystore -passout pass:keystore -export -out cert.pkcs12 -in cert.pem
  keytool -import -v -trustcacerts -alias default -file cert.pem -storepass truststore -keypass keystore -noprompt -keystore security/truststore.jks
  keytool -genkey -storepass testOnlyKeystore -keypass wefwef -keyalg RSA -alias endeca -keystore security/key.jks -dname CN=rsssl,OU=unknown,O=unknown,L=unknown,ST=unknown,C=CA
  keytool -delete -storepass testOnlyKeystore -alias endeca -keystore security/key.jks
  keytool -v -importkeystore -srcalias 1 -alias 1 -destalias default -noprompt -srcstorepass keystore -deststorepass testOnlyKeystore -srckeypass keystore -destkeypass testOnlyKeystore -srckeystore cert.pkcs12 -srcstoretype PKCS12 -destkeystore security/key.jks -deststoretype JKS
fi

if [ "$LOGSTASH_ENDPOINT" != "" ]; then
  /opt/ibm/wlp/bin/server start defaultServer
  echo Starting the logstash forwarder...
  sed -i s/PLACEHOLDER_LOGHOST/$LOGSTASH_ENDPOINT/g /opt/forwarder.conf
  cd /opt
  chmod +x ./forwarder
  echo -e $LOGSTASH_CERT > logstash-forwarder.crt
  echo -e $LOGSTASH_KEY > logstash-forwarder.key
  sleep 0.5
  ./forwarder --config ./forwarder.conf
else
  /opt/ibm/wlp/bin/server run defaultServer
fi
