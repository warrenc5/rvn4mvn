#!/bin/bash -x
cd `dirname $0`
MAVEN_OPTS="-Xms32m -Xmx256m -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5009"
export MAVEN_OPTS

while [ true ] ; do 
#mvn -Prun -Drvn.config=src/test/resources
mvn -Prun -Drvn.config=/home/wozza/.m2/
sleep 1
done
