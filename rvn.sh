#!/bin/bash -x
#JAVA_HOME=/usr/local/java/graalvm-ce-java11/
#LD_LIBRARY_PATH=$JAVA_HOME/lib/server/
PATH=$JAVA_HOME/bin:$PATH
export JAVA_HOME
export PATH
export LD_LIBRARY_PATH

cd `dirname $0`
MAVEN_OPTS="-Xms64m -Xmx512m"
EXEC_OPTS="-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5009"
export MAVEN_OPTS

while [ true ] ; do 
#mvn -v 
#mvn -s ~/.m2/settings.xml.none -DskipTests install
mvn -Prun -Dexec.opts="$EXEC_OPTS" -Drvn.args="$@"
#-Drvn.config=/Users/wc104415/.m2/
sleep 1
done
