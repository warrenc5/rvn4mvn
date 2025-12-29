#!/bin/bash 
#-i
#JAVA_HOME=/usr/local/java/graalvm-ce-java11/
#LD_LIBRARY_PATH=$JAVA_HOME/lib/server/
PATH=$JAVA_HOME/bin:$PATH
export JAVA_HOME
export PATH
export LD_LIBRARY_PATH
#JAVA_HOME=/home/wozza/.sdkman/candidates/java/25.0.1-graal
JAVA_HOME=/home/wozza/.sdkman/candidates/java/24.0.2-graal
export JAVA_HOME
#sdk use java 22.3.1.r11-grl

#set +x

ARGS=$@

YELLOW="\e[33m"
BLUE="\e[34m"
RESET="\e[0m"

echo -e ${YELLOW}---==== RVN ==== ---${RESET} 


if [[ "$ARGS" == "" || ${#ARGS[@]} == 0 ]] ; then
   ARGS=(`realpath $PWD`)
fi 

echo -e "${BLUE}${ARGS[@]}${RESET}"

D=$(dirname $(realpath -m $(readlink -f $0)))
MAVEN_OPTS="-Xms64m -Xmx512m"
EXEC_OPTS="-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5009"
export MAVEN_OPTS

#mvn -v 
#mvn -s ~/.m2/settings.xml.none -DskipTests install
function run() { 
mvn -s ~/.m2/settings.xml.none -f $D -Prun -Dexec.opts="$EXEC_OPTS" -Drvn.args="$ARGS"
#-Drvn.config=/Users/wc104415/.m2/
echo MAVEN exited with $?
}

if [[ $LOOP -eq 1 ]] ; then
while [ true ] ; do 
run
done
else 
run
fi
