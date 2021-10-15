#!/usr/bin/env groovy

/*
  This file is used for CoreMedia internal Deployment.
  Please ignore or delete.
*/

import com.coremedia.cm.DockerAgent
import com.coremedia.cm.Jenkins
import com.coremedia.cm.nexus.NexusStagingAction
import com.coremedia.comhub.ComhubHelper
import com.coremedia.comhub.releases.dto.ReleaseState

@org.jenkinsci.plugins.workflow.libs.Library(['coremedia-internal', 'coremedia-commerce', 'coremedia-aws']) _

final String PROJECT_NAME = 'headless-server-commerce'

final String RELEASE_DOCKER_REPOSITORY_NAME = 'coremedia'
final String RELEASE_DOCKER_IMAGE_NAME = 'headless-server-commerce'
final String RELEASE_LOCAL_STAGING_DIR = '${WORKSPACE}/target/nexus-staging'
final String RELEASES_JSON_FILE = 'releases.json'

final String DOCKER_IMAGE_MAVEN = "${Jenkins.globalDockerRegistry}/ci/coremedia-maven:3.6.3-amazoncorretto-11-2"
final String DOCKER_SNAPSHOTS_REGISTRY = "${Jenkins.getDockerRegistry(env)}/${RELEASE_DOCKER_REPOSITORY_NAME}"
final String DOCKER_RELEASES_REGISTRY = "${ComhubHelper.releasesCommerceRegistryUpstream}/${RELEASE_DOCKER_REPOSITORY_NAME}"

final Map<String, String> DEFAULT_MAVEN_PARAMS = [
        'docker.repository.prefix': DOCKER_SNAPSHOTS_REGISTRY,
        'enforcer.skip'           : 'true',
        'mdep.analyze.skip'       : 'true',
        'sort.skip'               : 'true',
        'sort.verifyFail'         : 'stop',
        'skipTests'               : 'true',
].asImmutable()

final String GITHUB_COMMIT_STATUS_CONTEXT = "${PROJECT_NAME} pipeline"

boolean isDefaultBuild = !params.BUILD_TYPE
boolean isReleaseStaging = params.BUILD_TYPE == 'RELEASE_STAGING'

boolean isTestMode = params.BRANCH.startsWith('test/')
String gitDryRun = isTestMode ? '--dry-run' : ''

String version

String releaseTag
String releaseNextDevelopmentVersion

pipeline {
  agent {
    label Jenkins.defaultNodeLabel
  }

  options {
    timestamps()
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '50', artifactNumToKeepStr: '25'))
    durabilityHint('PERFORMANCE_OPTIMIZED')
    newContainerPerStage()
  }

  stages {

    stage('Prepare') {
      steps {
        script {
          String projectVersion = comhubXmlStarletSelect(xmlFile: 'pom.xml', xPath: 'project/version')
          if (isReleaseStaging) {
            version = projectVersion.replace('-SNAPSHOT', '')
            cmBuildDescription(getUser: true, gitLink: true, information: ['Release Staging': version])
            env.RELEASE_VERSION = version // Show release version in build description of (outer) release pipeline
            releaseTag = "${PROJECT_NAME}-${version}"
            int nextDeVPatchVersion = 1 + Integer.valueOf(version.replaceAll('.*\\.', ''))
            releaseNextDevelopmentVersion = version.replaceAll('\\d+$', "${nextDeVPatchVersion}-SNAPSHOT")
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
        expression { isDefaultBuild || isReleaseStaging}
        beforeAgent true
      }
      agent {
        docker {
          image DOCKER_IMAGE_MAVEN
          args "${DockerAgent.defaultMavenArgs} ${DockerAgent.defaultDockerArgs}"
          reuseNode true
        }
      }
      stages {
        stage('Set Version') {
          steps {
            cmMaven(cmd: 'versions:set',
                    mavenParams: ['newVersion'        : "${version}",
                                  'generateBackupPoms': 'false',
                                  'processAllModules' : 'true'],
                    scanMvnLog: true,
            )
          }
        }
        stage('Build') {
          steps {
            cmMaven(cmd: 'deploy',
                    mavenParams: DEFAULT_MAVEN_PARAMS + (
                            isReleaseStaging
                                    ? ['performRelease'                : 'true',
                                       'altReleaseDeploymentRepository': "local::default::file://${RELEASE_LOCAL_STAGING_DIR}",
                                       'dockerfile.push.skip'          : 'true',
                                       'docker.repository.prefix'      : DOCKER_RELEASES_REGISTRY]
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
        stage('Javadoc') {
          steps {
            cmMaven(cmd: 'javadoc:javadoc',
                    mavenParams: DEFAULT_MAVEN_PARAMS,
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
        expression { isDefaultBuild || isReleaseStaging}
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
        stage('Stage Maven Artifacts') {
          when {
            expression { currentBuild.resultIsBetterOrEqualTo('SUCCESS') }
            beforeAgent true
          }
          agent {
            docker {
              image DOCKER_IMAGE_MAVEN
              args "${DockerAgent.defaultMavenArgs} ${DockerAgent.defaultDockerArgs}"
              reuseNode true
            }
          }
          steps {
            script {
              String stagingRepositoryId =
                      cmNexusStaging(action: NexusStagingAction.start, dryRun: isTestMode,
                              description: "Release ${PROJECT_NAME}-${version}")
              cmNexusStaging(action: NexusStagingAction.mavenDeploy, dryRun: isTestMode,
                      stagedRepositoryId: stagingRepositoryId,
                      localStagingDir: RELEASE_LOCAL_STAGING_DIR)
              cmNexusStaging(action: NexusStagingAction.close, dryRun: isTestMode,
                      stagedRepositoryId: stagingRepositoryId)
              comhubReleasesJsonAddRelease(file: RELEASES_JSON_FILE,
                      version: version,
                      stagingRepositoryId: stagingRepositoryId,
                      stateMavenArtifacts: ReleaseState.STAGED)
            }
          }
        }
        stage('Push Docker Image') {
          when {
            expression { currentBuild.resultIsBetterOrEqualTo('SUCCESS') }
            beforeAgent true
          }
          steps {
            script {
              comhubDockerPush(tag: "${DOCKER_RELEASES_REGISTRY}/${RELEASE_DOCKER_IMAGE_NAME}:${version}",
                      awsCredentialsId: ComhubHelper.ecrCredentialsId,
                      ec2Region: ComhubHelper.ecrRegion,
                      dryRun: isTestMode)
              comhubReleasesJsonUpdateRelease(file: RELEASES_JSON_FILE,
                      version: version,
                      stateDockerImage: ReleaseState.STAGED)
            }
          }
        }
/*         stage('Git Push Release') {
          when {
            expression { currentBuild.resultIsBetterOrEqualTo('SUCCESS') }
            beforeAgent true
          }
          steps {
            sh(label: 'Commit release version and create tag', returnStdout: true, script: """#!/usr/bin/env bash
set -euo pipefail
git add ${RELEASES_JSON_FILE}
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
              args "${DockerAgent.defaultMavenArgs} ${DockerAgent.defaultDockerArgs}"
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
        }*/
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
