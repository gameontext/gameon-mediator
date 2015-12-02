# Game On! 

[![Codacy Badge](https://api.codacy.com/project/badge/grade/1fc932713e474ba0bb9593a9cdcb8e35)](https://www.codacy.com/app/gameontext/gameon-mediator)

Game On! is a both a sample application, and throwback text adventure.


## Prereq

* gradle v2.7

## Build & development

Run live in WDT
Run `gradle build` to build the final server package for deployment.

## Testing

Gradle integration should mean running e2e tests for JS and Java app in one go (not there yet)

## Docker

To build a Docker image for this app/service, execute the following:

```
gradle buildImage
```

Or, if you don't have gradle, then:

```
./gradlew buildImage
```

### Interactive Run

```
docker run -it -p 9443:9443 --env-file=./dockerrc --name gameon-mediator gameon-mediator bash
```

Then, you can start the server with 
```
/opt/ibm/wlp/bin/server run defaultServer
```

### Daemon Run

```
docker run -d -p 9443:9443 --env-file=./dockerrc --name gameon-mediator gameon-mediator
```

### Stop

```
docker stop gameon-mediator ; docker rm gameon-mediator
```

### Restart Daemon

```
docker stop gameon-mediator ; docker rm gameon-mediator; docker run -d -p 9443:9443 --env-file=./dockerrc --name gameon-mediator gameon-mediator
```

