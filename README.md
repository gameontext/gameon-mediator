# Game On! Mediator Service

[![Codacy Badge](https://api.codacy.com/project/badge/grade/0635f617e5d2455ca24ba2fe5873a1ec)](https://www.codacy.com/app/gameontext/gameon-mediator)

See the mediator service [information page](https://gameontext.gitbooks.io/gameon-gitbook/content/microservices/mediator.html) in the Game On! Docs for more information on how to use this service.

## Building

To build this project:

    ./gradlew build
    docker build -t gameontext/gameon-mediator mediator-wlpcfg

## [MicroProfile](https://microprofile.io/)
MicroProfile is an open platform that optimizes the Enterprise Java for microservices architecture. In this application, we are using [**MicroProfile 1.3**](https://github.com/eclipse/microprofile-bom).

### Features
1. [MicroProfile Metrics](https://github.com/eclipse/microprofile-metrics) - This feature allows us to expose telemetry data. Using this, developers can monitor their services with the help of metrics.

    The application uses the `Timed` and `Counted` metrics. To access these metrics, go to https://localhost:9446/metrics.
    The Metrics feature is configured with SSL and can only be accessed through https. You will need to login using the username and password configured in the server.xml. The default values are `admin` and `admin`.

## Contributing

Want to help! Pile On! 

[Contributing to Game On!](CONTRIBUTING.md)
