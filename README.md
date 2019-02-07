# Raven 4 Maven

This is my file system monitor which runs maven when the project or repository filesystem changes. It's helpful to me for working on multimodule projects with interdependecies across other projects.

> alias rvn='java -jar ~/.m2/repository/mofokom/rvn/1.0-SNAPSHOT/rvn-1.0-SNAPSHOT.jar'

it supports some basic commands on stdin
! stop building the current running mvn process(es)
?[regex] search for artifacts and project files matching the regex e.g. mofokom::rvn:: or rvn4mvn/pom.xml
.*groupId::artifactId::.* build artifacts matching 
/code/project/path/to/pom.xml build artifact at 
$ reload configuration and rescan filesystem.

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
