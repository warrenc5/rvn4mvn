#!/bin/bash
WD=`pwd`
echo $WD
BASE=`dirname $0`
RUNNER=~/code/runner/
set +x
DIRS=`find $WD -type d -path '*src/main' -exec find {}/../.. -name pom.xml \; | xargs realpath | xargs dirname`
echo "---==== RUNNER ==== ---" 
$RUNNER/runner.sh $BASE/rvn.sh <<< `echo $BASE/rvn.sh $WD ${DIRS[@]}`
