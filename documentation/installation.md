# Installation

--------------------------------------------------------------------------------

\[[Up](README.md)\] \[[Top](#top)\]

--------------------------------------------------------------------------------

## Table of Content

1. [Building and running the server](#building-and-running-the-server)
1. [Release Download](#release-download)

## Building and running the server

Make sure that you do have access to the CoreMedia public maven repository.

Build the workspace with

    mvn clean install

Run the server with

    mvn spring-boot:run -pl headless-server-commerce-app -Dspring-boot.run.profiles=local,preview -Dinstallation.host=<CMS-SERVER-HOSTNAME>

or specify a profile defining the required property `installation.server.host`
. All prepared spring boot profiles can be
found [here](https://github.com/CoreMedia/coremedia-headless-commerce/tree/master/headless-server-commerce-app/src/main/resources)

The headless server commerce relies on running CoreMedia system. It connects to
a Content Server, reads the site settings and especially its livecontext
settings. The livecontext settings provide the commerce adapter endpoint. The
headless server commerce then establishes a connection to the commerce adapter
endpoint.

## Release Download

Goto [Release](https://github.com/CoreMedia/coremedia-headless-commerce/releases)
and download the version that matches your CMCC release version.

