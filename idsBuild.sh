#!/bin/bash

#
# This script is only intended to run in the IBM DevOps Services Pipeline Environment.
#

#!/bin/bash
echo Informing Slack...
curl -X 'POST' --silent --data-binary '{"text":"A new build for the player service has started."}' $WEBHOOK > /dev/null
echo Downloading Java 8...
wget http://game-on.org:8081/jdk-8u65-x64.tar.gz -q
echo Extracting Java 8...
tar xzf jdk-8u65-x64.tar.gz
echo And removing the tarball...
rm jdk-8u65-x64.tar.gz
export JAVA_HOME=$(pwd)/jdk1.8.0_65
echo Building projects using gradle...
./gradlew bowerInstall build 
echo Building and Starting Concierge Docker Image...
cd player-wlpcfg
../gradlew buildDockerImage removeCurrentContainer startNewContainer
echo Removing JDK, cause there's no reason that's an artifact...
cd ..
rm -rf jdk1.8.0_65