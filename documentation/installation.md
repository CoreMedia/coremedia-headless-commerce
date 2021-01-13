# Installation

--------------------------------------------------------------------------------

\[[Up](README.md)\] \[[Top](#top)\]

--------------------------------------------------------------------------------

## Table of Content

1. [Building and running the server](#building-and-running-the-server)
1. [Release Download](#release-download)
2. [Git Submodule](#git-submodule)
3. [Activate the extension](#activate-the-extension)
4. [Intellij IDEA Hints](#intellij-idea-hints)

## Building and running the server

Build the workspace with
    
    mvn clean install -Dinstallation.server.host=<CMS-SERVER-HOSTNAME> -Dinstallation.server.port=<CMS-SERVER-PORT>
    
or specify a global profile defining the required properties `installation.server.host` and `installation.server.port`.

Add the profile 

    -P performance-test
    
if you want to run the performance tests while building the workspace. The default setup requires the test data to be imported into the
CoreMedia content repository.

Run the server with

    mvn spring-boot:run -pl headless-server-app -Dinstallation.server.host=<CMS-SERVER-HOSTNAME> -Dinstallation.server.port=<CMS-SERVER-PORT>

or start the Tomcat Webapp

    mvn cargo:run -pl headless-server-webapp -Dinstallation.server.host=<CMS-SERVER-HOSTNAME> -Dinstallation.server.port=<CMS-SERVER-PORT>

### Executable Jar

A fully executable jar, which can be executed as binary or registered with `init.d` or `systemd`, can be created by adding the profile

    -P executable-jar`
    
at build time. For more information see ["Installing Spring Boot Applications"](https://docs.spring.io/spring-boot/docs/current/reference/html/deployment-install.html).


## Release Download

Go to [Release](https://github.com/CoreMedia/coremedia-headless-commerce/releases) and download the version that matches you CMCC release version.

From the Blueprint workspace's root folder, extract the ZIP file into `modules/extensions`.

Continue with [Activate the extension](#activate-the-extension).

## Git Submodule

From the Blueprint workspace's root folder, clone this repository or your fork as a submodule into the extensions folder. Make sure to use the branch name that matches your workspace version. A fork is required if you plan to customize the extension.

```
$ mkdir -p modules/extensions
$ cd modules/extensions
$ git submodule add https://github.com/<YOUR_ORGANIZATION>/coremedia-headless-commerce.git coremedia-headless-commerce
$ git submodule init
$ git checkout -b <your-branch-name>
```

Continue with [Activate the extension](#activate-the-extension).

## Activate the Extension

In order to activate the extension, you need to configure the extension tool. The configuration for the tool can be found under `workspace-configuration/extensions`. Make sure that you use at least version 4.0.1 of the extension tool and that you have adjusted the `<groupId>` and `<version>` so that they match your Blueprint workspace.

Here you need to add the following configuration for the `extensions-maven-plugin`
```xml
<configuration>
  <projectRoot>../..</projectRoot>
  <extensionsRoot>modules/extensions</extensionsRoot>
  <extensionPointsPath>modules/extension-config</extensionPointsPath>
</configuration>
```

After adapting the configuration run the extension tool in
`workspace-configuration/extensions` by executing:

```bash
$ mvn extensions:sync
$ mvn extensions:sync -Denable=<PROJECT_MVN_MODULE_NAME>
``` 

This will activate the extension. The extension tool will also set the relative path for the parents of the extension modules.

## Intellij IDEA Hints

For the IDEA import:
- Ignore folder ".remote-package"
- Disable "Settings > Compiler > Clear output directory on rebuild"
