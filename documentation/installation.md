# Installation

--------------------------------------------------------------------------------

\[[Up](README.md)\] \[[Top](#top)\]

--------------------------------------------------------------------------------

## Table of Content

1. [Introduction](#introduction)
1. [Release Download](#release-download)
2. [Git Submodule](#git-submodule)
3. [Activate the extension](#activate-the-extension)
4. [Intellij IDEA Hints](#intellij-idea-hints)

## Introduction

Depending on what you are setup and your plans, you can integrate this project in different ways.

* If you want to use the extension in your project, then it is recommended to fork the repository and integrate it as described in [Git Submodule](#git-submodule).
* If you do not want to use GitHub, proceed as described in [Release Download](#release-download).
* If you just want to contribute a new feature or a bugfix to the extension, you will need to work with the [Git Submodule](#git-submodule). As an external developer you will still need a fork of the repository to create a Pull Request. 

## Release Download

Go to [Release](https://github.com/CoreMedia/<PROJECT_REPO>/releases) and download the version that matches you CMCC release version.

From the Blueprint workspace's root folder, extract the ZIP file into `modules/extensions`.

Continue with [Activate the extension](#activate-the-extension).

## Git Submodule

From the Blueprint workspace's root folder, clone this repository or your fork as a submodule into the extensions folder. Make sure to use the branch name that matches your workspace version. A fork is required if you plan to customize the extension.

```
$ mkdir -p modules/extensions
$ cd modules/extensions
$ git submodule add https://github.com/<YOUR_ORGANIZATION>/<PROJECT_REPO>.git <PROJECT_REPO>
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
