# Raven 4 Maven

This is my file system monitor which runs maven when the filesystem changes.


> alias rvn='java -jar /code/rvn/target/rvn-1.0-SNAPSHOT.jar'


```
config = {
    "locations": [
        "/home/wozza/.m2/repository",
        "/code/captcha",
        "/code/rvn"
    ],

    "watchDirectories": {
        "includes": [
            ".*"
        ],
        "excludes": [
            ".*\\.nexus.*", ".*\\.index$", ".*target$", ".*org\\/.*"
        ]
    },
    "activeFiles": {
        "includes": [
            ".*pom.xml", ".*.pom$", ".*.java", ".*.xml", ".*.properties"
        ],
        "excludes": [
        ]
    },
    "activeArtifacts": {
        "includes": [
            ".*::.*"
        ],
        "excludes": [
            "org.*::.*"
        ]
    },
    "buildCommands": {
        "::::": "/opt/maven/bin/mvn -DskipTests -f %1$s clean install",
        "/code/captcha/.*": "/opt/maven/bin/mvn -DskipTests -f %1$s -N install",
        "mofokom::.*": "/opt/maven/bin/mvn -DskipTests -f %1$s install"
    }
}
```
