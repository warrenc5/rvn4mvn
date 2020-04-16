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

* Command          Example                         Description

* ?                ?                       - Prints the help.

* !                !                       - Stop the current build. Leave the build queue in place

* -                -                       - Hide the output.

* +                +                       - Show the output.

* |                |                       - Show the last failed output.

* .                .                       - Repeat last change that triggered a build.

* !!               !!                      - Stop the current build. Drain out the build queue

* >                >                       - Show the failMap

* @                @                       - Reload the configuration file and rescan filesystem.

* `                `::test::                       - List know project(s) matching coordinate or path expression.

* [groupId]::[artifactId]::[version]               ::test:: mygroup::                      - Builds the project(s) for the given coordinate(s). Supports regexp. e.g. .*::test::.* or ::test::

* path             /path/to/pom.xml                        - Builds the project(s) for the given coordinate(s). Supports regexp.

* path             /tmp/to/fail.out                        - Dump the file to stdout

* timeout {number}                 timeout 60                      - Sets the maximum build timeout to 1 minute.

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

echo fs.inotify.max_user_watches=524288 | sudo tee -a /etc/sysctl.conf && sudo sysctl -p
