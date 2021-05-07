![CoreMedia Labs Logo](https://documentation.coremedia.com/badges/banner_coremedia_labs_wide.png "CoreMedia Labs Logo")

![CoreMedia Content Cloud Version](https://img.shields.io/static/v1?message=2104&label=CoreMedia%20Content%20Cloud&style=for-the-badge&labelColor=666666&color=672779
"This badge shows the CoreMedia version this project is compatible with. Please read the versioning section of the project to see what other CoreMedia versions are supported and how to find them."
)
![Status](https://img.shields.io/static/v1?message=active&label=Status&style=for-the-badge&labelColor=666666&color=2FAC66
"The status badge describes if the project is maintained. Possible values are active and inactive. If a project is inactive it means that the development has been discontinued and won't support future CoreMedia versions."
)

# coremedia-headless-commerce

This open-source workspace contains a Commerce Headless Server. It provides a
GraphQL endpoint for commerce systems, which are connected via a CoreMedia
Commerce Adapter and the CoreMedia Commerce-Hub APIs.

The Commerce Headless Server builds on the CoreMedia Unified API, CoreMedia
Commerce-Hub API and makes use of modern technologies (Spring Boot, REST, JSON,
GraphQL). Integrate all Commerce Systems, which are supported by the CoreMedia
Commerce-Hub, and use this server as GraphQL endpoint. The Commerce Headless
Server establishes an UAPI connection to a Content Server and gRPC connections
to configured Commerce Adapters.

Please note that it is not recommended using the CoreMedia Headless Commerce
Server for production environments. Better use the REST or GraphQL endpoint of
your commerce system directly.

## Workspace Structure

The workspace is comprised of the following modules:

* **headless-server-commerce-app**: The Spring Boot Application packaged as
  executable JAR
* **headless-server-commerce-base-lib**: The base library module
* **headless-server-commerce-docker**: The Dockerfile
* **headless-server-commerce-lib**: The library module
* **headless-server-commerce-parent**: Parent module

## Versioning

To find out which CoreMedia versions are supported by this project, please take
look at the releases section or on the existing branches. To find the matching
version of your CoreMedia system, please checkout the branch with the
corresponding name. For example, if your CoreMedia version is 2104.1, checkout
the branch 2104.1.

## Documentation & Tutorial

* **[Documentation](documentation/README.md)**

    for guides for editors, administrators and developers    

* **[Changelog](CHANGELOG.md)**

    for recent changes

* **[Issues](https://github.com/CoreMedia/coremedia-headless-commerce/issues)**

    for known bugs and feature requests

## CoreMedia Labs

Welcome to [CoreMedia Labs](https://blog.coremedia.com/labs/)! This repository
is part of a platform for developers who want to have a look under the hood or
get some hands-on understanding of the vast and compelling capabilities of
CoreMedia. Whatever your experience level with CoreMedia is, we've got something
for you.

Each project in our Labs platform is an extra feature to be used with CoreMedia,
including extensions, tools and 3rd party integrations. We provide some test
data and explanatory videos for non-customers and for insiders there is
open-source code and instructions on integrating the feature into your CoreMedia
workspace.

The code we provide is meant to be example code, illustrating a set of features
that could be used to enhance your CoreMedia experience. We'd love to hear your
feedback on use-cases and further developments! If you're having problems with
our code, please refer to our issues section. If you already have a solution to
an issue, we love to review and integrate your pull requests.
