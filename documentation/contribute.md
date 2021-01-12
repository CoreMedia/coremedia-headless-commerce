# Contribute

--------------------------------------------------------------------------------

\[[Up](README.md)\] \[[Top](#top)\]

--------------------------------------------------------------------------------

## Table of Content

1. [How to Contribute Features and Fixes](#how-to-contribute-features-and-fixes)
1. [Branches and Tags](#-branches-and-tags)
    
## How to Contribute Features and Fixes   

As an external developer, we want to encourage you contributing to this extension, so that others can benefit from your fixes and features too. Contributing is as easy as:

1. Create an issue for your feature in the original repository on GitHub.
2. Fork the repository and implement your change.
3. Send us a Pull Request as described [here](https://help.github.com/en/github/collaborating-with-issues-and-pull-requests/creating-a-pull-request-from-a-fork) targeted at the `develop` branch.
    
Developers at CoreMedia don't necessarily need a fork of the repository. They have the required rights to create Pull Requests on the original repository.

## ⑃ Branches and Tags

> **This is of course only one approach on how you can manage branches in your project. Depending on your release cycle and development approach, there are of course other branch patterns that are better suited for your project.**
> **This branch model is our best practice if you want to deploy your extension by means of the CoreMedia CI.**    
>
> * **master:** Will be initially used to create `develop` branch. Afterwards, it will just be used to merge changes from `develop` branch to `master`, i.e., it will just be recipient afterwards. On _release_ the master merge commit will be tagged. See below for details on tagging.
> * **develop:** After initial creation, all development by CoreMedia and merging pull requests will happen here.
> * **ci/develop:** An artificial branch required for CoreMedia CI systems. It is required, as for CoreMedia CI we need to change the parent POMs in that way, that we set the version to `9999.9` and add a relative path, so that it matches our workspace setup.
>    
>    As soon as changes from `develop` shall be published to CI, we rebase
>    the adaptions:
>    
>    ```bash
>    $ git checkout "ci/develop"
>    $ git rebase "origin/develop"
>    $ git push --force-with-lease
>    ```
