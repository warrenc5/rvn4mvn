JAVA_HOME=/usr/local/java/graalvm-ce-java11/ mvn -Pnative install  -Dmaven.test.skip
cp target/rvn rvn
