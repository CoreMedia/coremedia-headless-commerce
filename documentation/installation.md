# Installation

--------------------------------------------------------------------------------

\[[Up](README.md)\] \[[Top](#top)\]

--------------------------------------------------------------------------------

## Table of Content

1. [Building and running the server](#building-and-running-the-server)
1. [Release Download](#release-download)

## Building and running the server

Build the workspace with

    mvn clean install

Run the server with

    mvn spring-boot:run -pl headless-server-commerce-app -Dspring-boot.run.profiles=local,preview -Dinstallation.host=<CMS-SERVER-HOSTNAME>

or specify a profile defining the required properties `installation.server.host`
. All prepared spring boot profiles can be
found [here](https://github.com/CoreMedia/coremedia-headless-commerce/tree/master/headless-server-commerce-app/src/main/resources)

## Release Download

Go to [Release](https://github.com/CoreMedia/coremedia-headless-commerce/releases) and download the version that matches you CMCC release version.

