#!/usr/bin/env groovy

properties([
  buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '1', numToKeepStr: '3')),
  parameters([
  	choice(defaultValue: "build", choices: ["build", "release", "update-dependencies"].join("\n"), description: '', name: 'build')
  ]),
  pipelineTriggers([cron('H 23 * * *')])
])

node {
   checkout scm
   checkout([$class: 'GitSCM',
       extensions: [[$class: 'CleanCheckout'],[$class: 'LocalBranch', localBranch: "master"]]])
   def mvnHome
   def dockerHome
   stage('Preparation') {
      mvnHome = tool 'Maven 3.5.2'
      dockerHome = tool 'docker-latest'
   }
   configFileProvider([configFile(fileId: '83ccdf5b-6b19-4cd7-93b6-fdffb55cefa9', variable: 'MAVEN_SETTINGS')])  {
	   stage('Build') {
	     if(params.build=='release'){
	       sh "'${mvnHome}/bin/mvn' -s $MAVEN_SETTINGS -Dresume=false clean release:prepare release:perform"     
	     }else if (params.build != 'update-dependencies'){
	       sh "'${mvnHome}/bin/mvn' -s $MAVEN_SETTINGS clean deploy -DperformRelease=true"
	     }
       }
   }
   stage('Docker build'){
     docker.withRegistry('https://docker-snapshot.berg-turbenthal.ch', '2190cf3e-ae3c-48a6-84ba-454a7e9a7b7c') {
       sh "rm server/target/server-*-javadoc.jar"
       sh "rm server/target/server-*-sources.jar"
       sh "'${dockerHome}/docker/docker' build server -t docker-snapshot.berg-turbenthal.ch/raoa-new-server:latest"
       sh "'${dockerHome}/docker/docker' push docker-snapshot.berg-turbenthal.ch/raoa-new-server:latest"
     }
   }
   stage('Results') {
      archive 'target/*.jar'
   }
}
