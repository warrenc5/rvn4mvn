# Raven 4 Maven

This is my file system monitor which runs maven when the project or repository filesystem changes. 

It's helpful to me for working on multimodule projects with interdependecies across other projects.

Features
* Runs as a cmdline application
* Generates config file on first run
* Reloads own configuration file
* Watches directories with *.poms in the .m2/repository 
* Watches project directories with pom.xml
* Build dependency graph and recompiles all affected projects.
* Custom mvn commands for each project

After installing 
set in your .profile

> alias rvn='java -jar ~/.m2/repository/mofokom/rvn/1.0-SNAPSHOT/rvn-1.0-SNAPSHOT.jar'

It also supports some basic commands on stdin
* ! - stop building the current running mvn process(es)
* ?[regex] - search for artifacts and project files matching the regex e.g. mofokom::rvn:: or rvn4mvn/pom.xml 
* .*groupId::artifactId::.* - build artifacts matching 
* code/project/path/to/pom.xml - build artifact at path or artifacts matching path
* $ - reload configuration and rescan filesystem.

Config file lives at ~/.m2/rvn.json
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
