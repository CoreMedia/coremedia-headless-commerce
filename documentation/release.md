# Release (CoreMedia-only)

--------------------------------------------------------------------------------

\[[Up](README.md)\] \[[Top](#top)\]

--------------------------------------------------------------------------------

## Table of Content

1. [Documentation Update](#documentation-update)
1. [Release Steps](#release-steps)
1. [Post Process](#post-process)

## Documentation Update

* Ensure you have built the CMCC version (snapshot versions) which this
    workspace dedicates to. Otherwise, the third-party versions won't
    match the declared CMCC version (most third-party dependencies)
    are managed in Blueprint and CMCC Core.

* Update [THIRD-PARTY.txt](../THIRD-PARTY.txt) and license downloads either manually or by running if you are using Maven and Java:

    ```bash
    $ mvn -Pdocs-third-party generate-resources
    ```
  
  Your extensions root POM has to contain the following configuration to make this work:
  
  ```xml
    <build>
      <pluginManagement>
        <plugins>
          <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>license-maven-plugin</artifactId>
            <version>2.0.0</version>
            <configuration>
              <cleanLicensesOutputDirectory>true</cleanLicensesOutputDirectory>
              <errorRemedy>ignore</errorRemedy>
              <excludedGroups>^com\.coremedia\.</excludedGroups>
              <excludeTransitiveDependencies>true</excludeTransitiveDependencies>
              <includeTransitiveDependencies>false</includeTransitiveDependencies>
              <licensesOutputFile>${docs.directory}/third-party-licenses/licenses.xml</licensesOutputFile>
              <licensesOutputDirectory>${docs.directory}/third-party-licenses</licensesOutputDirectory>
              <outputDirectory>${docs.directory}</outputDirectory>
              <sortByGroupIdAndArtifactId>true</sortByGroupIdAndArtifactId>
            </configuration>
          </plugin>
        </plugins>
      </pluginManagement>
    </build>
    
    <profiles>
      <profile>
        <id>docs-third-party</id>
        <!--
          Will create generated resources for docs/ folder.
        -->
        <build>
          <plugins>
            <plugin>
              <groupId>org.codehaus.mojo</groupId>
              <artifactId>license-maven-plugin</artifactId>
              <executions>
                <execution>
                  <id>generate-docs-licenses</id>
                  <goals>
                    <goal>aggregate-add-third-party</goal>
                    <goal>aggregate-download-licenses</goal>
                  </goals>
                  <phase>generate-resources</phase>
                </execution>
              </executions>
            </plugin>
          </plugins>
        </build>
      </profile>
    </profiles>
  ```

* Update badges at main workspace `README.md`.
    
## Release Steps

> **Depending on your [branch model](contribute.md#-branches-and-tags) and your requirements you do not necessarily need to build releases. Releases can also be built by means of GitHub. Not having to build releases is certainly easier. At the same time they make it easier to identify which changes have been made in which version to find the best version for the corresponding project workspace.** 
>
> Assuming all branches (master, develop, ci/develop) already exist, proceed as follows:
>
> ```bash
> $ git clone https://github.com/CoreMedia/<PROJECT_NAME>.git
> $ cd <PROJECT_NAME>
> $ git checkout --track "origin/develop"
> # ... perform required updates ...
> $ git commit --all --message="Fixed #1"
> # ... perform required updates ...
> $ git commit --all --message="Update #2"
> $ git push origin develop 
> $ git checkout --track "origin/ci/develop"
> $ git rebase "origin/develop"
> $ git push origin "ci/develop" --force-with-lease
> 
> ### It is recommended to leave "ci/develop" immediately, as no other commits
> ### must make it to this branch than those required to run it in CoreMedia CI!
> 
> $ git checkout develop
> ```
> 
> Prior to releasing, ensure to update documentation links and third-party reports
> (see [Documentation Update](#documentation-update)) and to adapt the `CHANGELOG.md`.
> 
> The structure of tags is as follows:
> 
> ```text
> <CMCC_VERSION>-<PROJECT_VERSION>
> ```
> 
> Thus, `1907.1-1` signals compatibility with CMCC 1907.1 and is the first
> version of this extension. `1907.1-2` is a patch version for
> version `1907.1-1`, which is based on the same CMCC version, but for example
> contains bug fixes.
> 
> 
> ```bash
> $ git checkout master
> $ git merge "origin/develop"
> $ git push origin master
> $ git tag "1910.1-1"
> $ git push origin "1910.1-1"
> ```

## Post Process

* Review GitHub issues and possibly adjust state.
* Close and possibly rename the milestone.
