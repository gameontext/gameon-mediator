#!/bin/bash

#
# This script is only intended to run in the IBM DevOps Services Pipeline Environment.
#

#!/bin/bash
echo Informing Slack...
curl -X 'POST' --silent --data-binary '{"text":"A new build for the mediator service has started."}' $WEBHOOK > /dev/null

mkdir dockercfg ; cd dockercfg
echo Downloading Docker requirements..
wget --user=admin --password=$ADMIN_PASSWORD https://$BUILD_DOCKER_HOST:8443/dockerneeds.tar -q
echo Setting up Docker...
tar xzf dockerneeds.tar
cd ..

echo Building projects using gradle...
./gradlew build
echo Building and Starting Mediator Docker Image...
cd mediator-wlpcfg
sed -i s/PLACEHOLDER_ADMIN_PASSWORD/$ADMIN_PASSWORD/g ./Dockerfile

../gradlew buildDockerImage
../gradlew stopCurrentContainer
../gradlew removeCurrentContainer
../gradlew startNewContainer
