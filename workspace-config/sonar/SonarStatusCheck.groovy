#!/usr/bin/env groovy

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

if (args.length != 1) {
  throw new IllegalArgumentException('Missing argument! Usage: ./SonarStatusCheck.groovy <sonarReportFileName>')
}

String sonarReportFileName = args[0]

new SonarStatusChecker().exec(sonarReportFileName)

class SonarStatusChecker {
  static final List<String> RELEVANT_METRICS = ['blocker_violations', 'critical_violations']
  static final int TIMEOUT_MINUTES = 30
  static final int RETRY_DELAY_SECONDS = 30

  void exec(String reportFileName) {
    if (!reportFileName) {
      throw new IllegalArgumentException("Missing parameter 'reportFileName'")
    }

    // Necessary due to self-signed certificate of the Sonar Server
    trustEverybody()

    SonarReport sonarReport = new SonarReport(readPropertiesFile(reportFileName))
    println "Sonar Report: ${JsonOutput.prettyPrint(JsonOutput.toJson(sonarReport))}"

    long timeoutTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(TIMEOUT_MINUTES)

    while (!computationReady(sonarReport)) {
      if (System.currentTimeMillis() > timeoutTime) {
        throw new IllegalStateException("timeout: Remote Sonar computation didn't finish in ${TIMEOUT_MINUTES} minutes")
      }
      println "Remote Sonar computation didn't finish, yet; Waiting ${RETRY_DELAY_SECONDS} seconds..."
      Thread.sleep(RETRY_DELAY_SECONDS * 1000L)
    }

    if (hasRelevantIssues(sonarReport)) {
      throw new IllegalStateException("There are relevant Sonar Issues")
    }
  }

  private static boolean computationReady(SonarReport sonarReport) {
    def ceJson = new JsonSlurper().parseText(sonarReport.ceTaskUrl.text)
    def status = ceJson.task.status

    if (status == 'PENDING' || status == 'IN_PROGRESS') {
      return false
    }

    if (status == 'SUCCESS') {
      return true
    }

    throw new IllegalStateException("unexpected task state: '${status}'; expected either 'SUCCESS', 'PENDING' or 'IN_PROGRESS'")
  }

  private boolean hasRelevantIssues(SonarReport sonarReport) {
    String measuresUrlString = "${sonarReport.serverUrl.toString()}/api/measures/component?metricKeys=${RELEVANT_METRICS.join(',')}&component=${sonarReport.projectKey}"

    println "Fetching measures from '${measuresUrlString}'"

    URL measuresUrl = measuresUrlString.toURL()
    Object measuresJson = new JsonSlurper().parseText(measuresUrl.text)

    List relevantMeasures = measuresJson.component.measures.findAll {
      RELEVANT_METRICS.contains(it.metric)
    }

    println "Measures: ${JsonOutput.prettyPrint(JsonOutput.toJson(relevantMeasures))}"

    relevantMeasures.any {
      it.value != 0 && it.value != '0'
    }
  }

  private Properties readPropertiesFile(String fileName) {
    Properties properties = new Properties()
    InputStream inputStream = null
    try {
      inputStream = new File(fileName).newInputStream()
      properties.load(inputStream)
    } catch (Exception e) {
      println "[ERROR] ${e.message}"
      throw e
    } finally {
      inputStream.close()
    }
    properties
  }

  private static void trustEverybody() {
    TrustManager[] trustAllCerts = [new X509TrustManager() {
      X509Certificate[] getAcceptedIssuers() {
        null
      }

      void checkClientTrusted(X509Certificate[] certs, String authType) {
      }

      void checkServerTrusted(X509Certificate[] certs, String authType) {
      }
    }] as TrustManager[]

    SSLContext sc = SSLContext.getInstance("TLS")
    sc.init(null, trustAllCerts, new SecureRandom())
    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory())
  }

  private static class SonarReport {
    final String projectKey
    final URL serverUrl
    final String serverVersion
    final URL dashboardUrl
    final String ceTaskId
    final URL ceTaskUrl

    SonarReport(Properties properties) {
      projectKey = properties.get('projectKey')
      serverUrl = ((String) properties.get('serverUrl')).toURL()
      serverVersion = properties.get('serverVersion')
      dashboardUrl = ((String) properties.get('dashboardUrl')).toURL()
      ceTaskId = properties.get('ceTaskId')
      ceTaskUrl = ((String) properties.get('ceTaskUrl')).toURL()
    }
  }
}
