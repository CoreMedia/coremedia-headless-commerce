#!/usr/bin/env groovy

/*
  This file is used for CoreMedia internal Deployment.
  Please ignore or delete.
*/

import com.coremedia.cm.DockerAgent
import com.coremedia.cm.Jenkins
import com.coremedia.comhub.ComhubHelper

@org.jenkinsci.plugins.workflow.libs.Library(['coremedia-internal', 'coremedia-commerce', 'coremedia-aws-v2']) _

final String PROJECT_NAME = 'headless-server-commerce'

final String RELEASE_DOCKER_REPOSITORY_NAME = 'coremedia'
final String RELEASE_DOCKER_IMAGE_NAME = PROJECT_NAME
final String RELEASE_LOCAL_STAGING_DIR = '${WORKSPACE}/target/nexus-staging'

final String DOCKER_IMAGE_MAVEN = "${Jenkins.ecrPullThroughProxyRegistry}/cm-tools/maven:3.8.6-17.0.9.8-1-cm-1.1.1"
final String DOCKER_SNAPSHOTS_REGISTRY = "${Jenkins.getDockerRegistry(env)}/${RELEASE_DOCKER_REPOSITORY_NAME}"
final String DOCKER_RELEASES_REGISTRY = "${ComhubHelper.releasesCommerceRegistryUpstream}/${RELEASE_DOCKER_REPOSITORY_NAME}"

final Map<String, String> DEFAULT_MAVEN_PARAMS = [
        'application.image-prefix'   : DOCKER_SNAPSHOTS_REGISTRY,
        'jib.allowInsecureRegistries': 'true',
        'jib.goal'                   : 'build',
        'enforcer.skip'              : 'true',
        'mdep.analyze.skip'          : 'true',
        'sort.skip'                  : 'true',
        'sort.verifyFail'            : 'stop',
        'skipTests'                  : 'true',
        'maven.javadoc.skip'         : 'true',
].asImmutable()

final String GITHUB_COMMIT_STATUS_CONTEXT = "${PROJECT_NAME} pipeline"

boolean isDefaultBuild = !params.BUILD_TYPE
boolean isReleaseStaging = params.BUILD_TYPE == 'RELEASE_STAGING'

boolean isTestMode = params.BRANCH.startsWith('test/')
String gitDryRun = isTestMode ? '--dry-run' : ''

String version

String releaseTag
String releaseNextDevelopmentVersion

String tmpDockerImageTagRelease

pipeline {
  agent {
    label Jenkins.defaultNodeLabel
  }

  options {
    disableResume()
    timestamps()
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '10'))
    durabilityHint('PERFORMANCE_OPTIMIZED')
    newContainerPerStage()
  }

  stages {
    stage('Prepare') {
      steps {
        script {
          String projectVersion = comhubXmlStarletSelect(xmlFile: 'pom.xml', xPath: 'project/version')
          if (isReleaseStaging) {//input example: 2.0.40-RC-SNAPSHOT
            version = projectVersion.replace('-SNAPSHOT', '') //example: 2.0.40-RC
            String versionExtension = version.replaceAll('[\\d.]*','') //example: -RC
            tmpDockerImageTagRelease = "${version}-tmp-rc-SNAPSHOT"
            cmBuildDescription(getUser: true, gitLink: true, information: ['Release Staging': version])
            env.RELEASE_VERSION = version // Show release version in build description of (outer) release pipeline
            releaseTag = "${PROJECT_NAME}-${version}"
            int nextDeVPatchVersion = 1 + Integer.valueOf(version.replaceAll('.*\\.', '').replaceAll('[^\\d.]*','')) //example: 41
            releaseNextDevelopmentVersion = version.replaceAll("\\d+${versionExtension}", "${nextDeVPatchVersion}${versionExtension}-SNAPSHOT") //example: 2.0.41-RC-SNAPSHOT
            cmGitLocalAuth()
          } else {
            String gitShortRef = sh(label: 'Get short ref from HEAD commit', returnStdout: true,
                    script: 'git rev-parse --short HEAD').trim()
            version = projectVersion.replace('-SNAPSHOT', "-${gitShortRef}-SNAPSHOT")
            cmBuildDescription(getUser: true, gitLink: true, information: ['Build Version': version])
            cmSetGitCommitStatus(context: GITHUB_COMMIT_STATUS_CONTEXT, repository: PROJECT_NAME)
          }
        }
      }
    }

    stage('Maven') {
      when {
        expression { isDefaultBuild || isReleaseStaging }
        beforeAgent true
      }
      agent {
        docker {
          image DOCKER_IMAGE_MAVEN
          args DockerAgent.defaultMavenArgs
          reuseNode true
        }
      }
      stages {
        stage('Set Version') {
          steps {
            cmMaven(cmd: 'versions:set',
                    mavenParams: ['newVersion'            : "${version}",
                                  'generateBackupPoms'    : 'false',
                                  'updateMatchingVersions': 'false', // https://stackoverflow.com/questions/16865743/updating-the-versions-in-a-maven-multi-module-project
                                  'processAllModules'     : 'true'],
                    scanMvnLog: true,
            )
          }
        }
        stage('Build') {
          steps {
            cmMaven(cmd: 'deploy -Pdefault-image',
                    mavenParams: DEFAULT_MAVEN_PARAMS + (
                            isReleaseStaging
                                    ? ['performRelease'                : 'true',
                                       'altReleaseDeploymentRepository': "local::default::file://${RELEASE_LOCAL_STAGING_DIR}",
                                       'application.image-tag'         : tmpDockerImageTagRelease]
                                    : [:]),
                    scanMvnLog: true,
            )
          }
        }
        stage('Test') {
          steps {
            cmMaven(cmd: 'verify -Pintegration-test',
                    mavenParams: DEFAULT_MAVEN_PARAMS + ['skipTests': 'false'],
                    scanMvnLog: true,
            )
          }
          post {
            always {
              junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml,**/target/failsafe-reports/*.xml'
            }
          }
        }
        stage('Check Enforcer Rules') {
          steps {
            cmMaven(cmd: 'validate',
                    mavenParams: DEFAULT_MAVEN_PARAMS + ['enforcer.skip': 'false'],
                    scanMvnLog: true,
            )
          }
        }
        stage('Check Dependencies') {
          steps {
            cmMaven(cmd: 'verify',
                    mavenParams: DEFAULT_MAVEN_PARAMS + ['mdep.analyze.skip': 'false'],
                    scanMvnLog: true,
            )
          }
        }
        stage('Check POM Formatting') {
          steps {
            cmMaven(cmd: 'validate',
                    mavenParams: DEFAULT_MAVEN_PARAMS + ['sort.skip': 'false'],
                    scanMvnLog: true,
            )
          }
        }
        stage('Sonar') {
          steps {
            catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
              cmMaven(cmd: 'sonar:sonar -Pci-sonar',
                      mavenParams: ['sonar.userHome'  : "${env.WORKSPACE}",
                                    'sonar.projectKey': "${PROJECT_NAME}_${Jenkins.getNodeName(env.JENKINS_URL)}_${env.BRANCH.replace('/', '-')}"],
                      useJavaTrustStore: true,
                      scanMvnLog: true,
              )
            }
          }
          post {
            success {
              archiveArtifacts artifacts: "**/target/sonar/report-task.txt"
            }
          }
        }
      }
    }
    stage('Checks Sonar Results') {
      when {
        expression { isDefaultBuild || isReleaseStaging }
        beforeAgent true
      }
      agent {
        docker {
          image 'groovy:2.5.8-jdk11'
          args params.DOCKER_BUILD_NODE_ARGS
          reuseNode true
        }
      }
      steps {
        catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
          sh(label: "Check Sonar results", returnStdout: false,
                  script: "groovy workspace-config/sonar/SonarStatusCheck.groovy 'target/sonar/report-task.txt'")
        }
      }
    }
    stage('Release Staging') {
      when {
        expression { isReleaseStaging }
        beforeAgent true
      }
      stages {
        stage('Push Docker Image') {
          when {
            expression { currentBuild.resultIsBetterOrEqualTo('SUCCESS') }
            beforeAgent true
          }
          steps {
            script {
              String tmpImage = "${DOCKER_SNAPSHOTS_REGISTRY}/${RELEASE_DOCKER_IMAGE_NAME}:${tmpDockerImageTagRelease}"
              String releaseImage = "${DOCKER_RELEASES_REGISTRY}/${RELEASE_DOCKER_IMAGE_NAME}:${version}"
              cmBash(label: 'Pull and tag release image', returnStdout: true,
                      script: "docker pull ${tmpImage} && docker tag ${tmpImage} ${releaseImage}")
              comhubDockerPush(tag: "${releaseImage}",
                      awsCredentialsId: ComhubHelper.ecrCredentialsId,
                      ec2Region: ComhubHelper.ecrRegion,
                      dryRun: isTestMode)
            }
          }
        }
        stage('Git Push Release') {
          when {
            expression { currentBuild.resultIsBetterOrEqualTo('SUCCESS') }
            beforeAgent true
          }
          steps {
            sh(label: 'Commit release version and create tag', returnStdout: true, script: """#!/usr/bin/env bash
set -euo pipefail
git ls-files --modified | xargs -n1 git add
git commit --message="Release ${releaseTag}"
git push origin HEAD:refs/heads/${params.BRANCH}
git tag --annotate --message="Release ${releaseTag}" ${releaseTag}
git push ${gitDryRun} origin refs/tags/${releaseTag}:refs/tags/${releaseTag}
""")
          }
        }
        stage('Set next SNAPSHOT') {
          when {
            expression { currentBuild.resultIsBetterOrEqualTo('SUCCESS') }
            beforeAgent true
          }
          agent {
            docker {
              image DOCKER_IMAGE_MAVEN
              args DockerAgent.defaultMavenArgs
              reuseNode true
            }
          }
          steps {
            cmMaven(cmd: 'versions:set',
                    mavenParams: ['newVersion'        : "${releaseNextDevelopmentVersion}",
                                  'generateBackupPoms': 'false',
                                  'processAllModules' : 'true'],
                    scanMvnLog: true,
            )
          }
        }
        stage('Git Push next SNAPSHOT') {
          when {
            expression { currentBuild.resultIsBetterOrEqualTo('SUCCESS') }
            beforeAgent true
          }
          steps {
            sh(label: 'Commit next dev version and push to remote', returnStdout: true, script: """#!/usr/bin/env bash
set -euo pipefail
git ls-files --modified | xargs -n1 git add
git commit --message="Set next development version ${releaseNextDevelopmentVersion}"
git push origin HEAD:refs/heads/${params.BRANCH}
""")
          }
        }
      }
    }
  }
  post {
    always {
      script {
        if (isDefaultBuild) {
          cmSetGitCommitStatus(context: GITHUB_COMMIT_STATUS_CONTEXT, repository: PROJECT_NAME)
        }
      }
      cmsCleanup()
    }
  }
}
