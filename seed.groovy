/*
This variable controls the publishing location (nexus or artifactory)
Possible values are "artifactory" or "nexus"
*/
def artifactRepositoryType = "artifactory"

def workspaceDir = new File(__FILE__).getParentFile()

def gradleInitFileId = "gradle-init-gradle"
def gradleInitRepoFile = "moderne-init.gradle"
def gradleInitLocation = artifactRepositoryType == "artifactory" ? "gradle/init-artifactory.gradle" : "gradle/init-nexus.gradle"

def mavenIngestSettingsXmlFileId = "maven-ingest-settings-credentials"
def mavenIngestSettingsXmlRepoFile = ".mvn/ingest-settings.xml"

def mavenAddMvnConfigShellFileId = "maven-add-mvn-configuration.sh"
def mavenAddMvnConfigShellRepoLocation = ".mvn/add-mvn-configuration.sh"

folder('ingest') {
    displayName('Ingest Jobs')
}

folder('validate') {
    displayName('Recipe Run Validation Jobs')
}

configFiles {
    groovyScript {
        id(gradleInitFileId)
        name("Gradle: init.gradle")
        comment("A Gradle init script used to inject universal plugins into a gradle build.")
        content readFileFromWorkspace(gradleInitLocation)
    }
    customConfig {
        id(mavenAddMvnConfigShellFileId)
        name("Maven: add-mvn-configuration.sh")
        comment("A shell script that will adds custom mvn configurations to a Maven Build")
        content readFileFromWorkspace('maven/add-mvn-configuration.sh')
    }
    mavenSettingsConfig {
        id(mavenIngestSettingsXmlFileId)
        name("Maven Settings: ingest-maven-settings.xml")
        comment("Maven settings that sets mirror on repos that are known to use http, and injects artifactory credentials")
        content readFileFromWorkspace('maven/ingest-settings.xml')
        isReplaceAll(true)
        // serverCredentialMappings {
        //     serverCredentialMapping {
        //         serverId('moderne-remote-cache')
        //         credentialsId('artifactory')
        //     }
        // }
    }
}
new File(workspaceDir, 'repos.csv').splitEachLine(',') { tokens ->
    if (tokens[0].startsWith('repoName')) {
        return
    }
    def repoName = tokens[0]
    def repoBranch = tokens[1]
    def repoJavaVersion = tokens[2]
    def repoStyle = tokens[3]
    def repoBuildTool = tokens[4]
    def repoBuildAction = tokens[5]
    def repoSkip = tokens[6]

    if (repoBuildAction == null) {
        repoBuildAction = ''
    }
    if ('true' == repoSkip) {
        return
    }

    def repoJobName = repoName.replaceAll('/', '_')

    boolean isGradleBuild = ['gradle', 'gradlew'].contains(repoBuildTool)
    boolean isMavenBuild = repoBuildTool != null && (repoBuildTool.startsWith("maven") || repoBuildTool.equals("mvnw"))
    //The latest version of maven is used if the repoBuildTool is just "maven", otherwise the name repoBuildTool is treated as the jenkins name.
    def jenkinsMavenName = repoBuildTool != null && repoBuildTool == "maven" ? "maven3.x" : repoBuildTool


    println("creating job $repoJobName")
    // TODO figure out how to store rewrite version, look it up on next run, and if rewrite hasn't changed and commit hasn't changed, don't run.

    job("ingest/$repoJobName") {

        label('multi-jdk')

        jdk("java${repoJavaVersion}")

        // environmentVariables {
        //     env('ANDROID_HOME', '/usr/lib/android-sdk')
        //     env('ANDROID_SDK_ROOT', '/usr/lib/android-sdk')
        // }

        logRotator {
            daysToKeep(30)
        }

        scm {
            git {
                remote {
                    url("https://github.com/${repoName}")
                    branch(repoBranch)
                    // credentials('cloning-creds') // Jenkins credential ID
                }
                extensions {
                    localBranch(repoBranch)
                }
            }
        }

        triggers {
            cron('H 4 * * *')
        }

        wrappers {
            if (artifactRepositoryType == 'artifactory') {
                credentialsBinding {
                    usernamePassword('ARTIFACTORY_USER', 'ARTIFACTORY_PASSWORD', 'artifactory')
                }
            } else if (artifactRepositoryType == 'nexus') {
                credentialsBinding {
                    usernamePassword('NEXUS_CREDENTIALS', 'nexus')
                }
            }
            timeout {
                absolute(60)
                abortBuild()
            }
            if (isGradleBuild) {
                configFiles {
                    file(gradleInitFileId) {
                        targetLocation(gradleInitRepoFile)
                    }
                }
            }
            if (isMavenBuild) {
                configFiles {
                    file(mavenIngestSettingsXmlFileId) {
                        targetLocation(mavenIngestSettingsXmlRepoFile)
                    }
                    file(mavenAddMvnConfigShellFileId) {
                        targetLocation(mavenAddMvnConfigShellRepoLocation)
                    }
                }
            }
        }

        if (isGradleBuild) {
            steps {
                gradle {
                    if (repoBuildTool == 'gradle') {
                        useWrapper(false)
                        gradleName('gradle 7.4.2')
                    } else {
                        useWrapper(true)
                        makeExecutable(true)
                    }
                    if (repoStyle != null) {
                        switches("--no-daemon -Dskip.tests=true -DactiveStyle=${repoStyle} -I ${gradleInitRepoFile} -Dorg.gradle.jvmargs=-Xmx2048M ${repoBuildAction}")
                    } else {
                        switches("--no-daemon -Dskip.tests=true -I ${gradleInitRepoFile} -Dorg.gradle.jvmargs=-Xmx2048M ${repoBuildAction}")
                    }
                    if (artifactRepositoryType == 'artifactory') {
                        tasks('clean moderneJar artifactoryPublish')
                    } else {
                        tasks('clean moderneJar')
                    }
                }
            }
        }

        if (isMavenBuild) {
            steps {
                // Adds a shell script into the Jobs workspace in /tmp.
                shell("bash ${mavenAddMvnConfigShellRepoLocation}")
            }
            configure { node ->

                node / 'builders' << 'org.jfrog.hudson.maven3.Maven3Builder' {
                    mavenName jenkinsMavenName
                    useWrapper(repoBuildTool == 'mvnw')

                    goals "-B -DpomCacheDirectory=. -Drat.skip=true -Dlicense.skip=true -Dlicense.skipCheckLicense=true -Drat.numUnapprovedLicenses=100 -Dgpg.skip -Darchetype.test.skip=true -Dmaven.findbugs.enable=false -Dspotbugs.skip=true -Dpmd.skip=true -Dcpd.skip=true -Dfindbugs.skip=true -DskipTests -DskipITs -Dcheckstyle.skip=true -Denforcer.skip=true -Dskip.npm -Dskip.yarn -Dskip.bower -Dskip.grunt -Dskip.gulp -Dskip.jspm -Dskip.karma -Dskip.webpack -s ${mavenIngestSettingsXmlRepoFile} ${(repoStyle != null) ? "-Drewrite.activeStyle=${repoStyle}" : ''} -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn ${repoBuildAction} clean install io.moderne:moderne-maven-plugin:0.32.2:ast"
                }

                if (artifactRepositoryType == 'artifactory') {
                    node / 'buildWrappers' << 'org.jfrog.hudson.maven3.ArtifactoryMaven3Configurator' {
                        deployArtifacts true
                        deploymentProperties 'moderne_parsed=true'
                        artifactDeploymentPatterns {
                            includePatterns '*-ast.jar'
                        }
                        deployerDetails {
                            artifactoryName 'moderne-maven'
                            deployReleaseRepository {
                                keyFromText 'moderne-maven-repo'
                            }
                            deploySnapshotRepository {
                                keyFromText 'moderne-maven-repo'
                            }
                        }
                    }
                }
            }
        }

        if (artifactRepositoryType == 'nexus') {
            steps {
                groovyCommand(readFileFromWorkspace('publish-ast.groovy'))
            }
        }

        publishers {
            cleanWs()
        }
    }

    job("validate/$repoJobName") {
        parameters {
            stringParam('buildName')
            stringParam('patchDownloadUrl')
        }
        label('multi-jdk')

        jdk("java${repoJavaVersion}")

        environmentVariables {
            env('ANDROID_HOME', '/usr/lib/android-sdk')
            env('ANDROID_SDK_ROOT', '/usr/lib/android-sdk')
        }

        logRotator {
            daysToKeep(30)
        }

        scm {
            git {
                remote {
                    url("https://github.com/${repoName}")
                    branch(repoBranch)
                    // credentials('cloning-creds')
                }
                extensions {
                    localBranch(repoBranch)
                }
            }
        }

        wrappers {
            buildName('${buildName}')
        }

        steps {
            systemGroovyCommand(readFileFromWorkspace('groovy/apply-patch.groovy'))
        }

        if (isGradleBuild) {
            steps {
                gradle {
                    tasks('assemble')
                }
            }
        }

        if (isMavenBuild) {
            configure { node ->

                node / 'builders' << 'org.jfrog.hudson.maven3.Maven3Builder' {
                    mavenName jenkinsMavenName
                    useWrapper(repoBuildTool == 'mvnw')

                    goals 'compile'
                }
            }
        }

        publishers {
            cleanWs()
        }
    }
    return
}
