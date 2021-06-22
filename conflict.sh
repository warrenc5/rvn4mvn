#!/bin/bash -x
tar cvf conflict-`date -I`.tar `find . -name *\conflict\*.java`
for i in `find . -name \*sync-conflict\* -name \*.java` ; do 

O=`echo $i | sed 's/\.sync-conflict.*\././'`
gvim -d $O $i 
done
#find . -name *\conflict\*.java --delete
