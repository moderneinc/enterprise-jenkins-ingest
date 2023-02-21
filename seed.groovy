def workspaceDir = new File(__FILE__).getParentFile()

def mavenIngestSettingsXmlFileId = "maven-ingest-settings-credentials"
def mavenIngestSettingsXmlRepoFile = ".mvn/ingest-settings.xml"

def publishURL = 'https://artifactory.acme.com/artifactory/moderne-ingest'
def publishCreds = 'artifactory'
def azureCreds = 'azure'
def scmCredentialsId = 'cloning-creds'
def scheduling='H 4 * * *'
def moderneCLIVersion= '0.0.8'

folder('ingest') {
    displayName('Ingest Jobs')
}

configFiles {
    mavenSettingsConfig {
        id(mavenIngestSettingsXmlFileId)
        name("Maven Settings: ingest-maven-settings.xml")
        comment("Maven settings that sets mirror on repos that are known to use http, and injects artifactory credentials")
        content readFileFromWorkspace('maven/ingest-settings.xml')
        isReplaceAll(true)
    }
}

new File(workspaceDir, 'repos.csv').splitEachLine(',') { tokens ->
    if (tokens[0].startsWith('repoName')) {
        return
    }
    def scmHost = tokens[0]
    def repoName = tokens[1]
    def repoBranch = tokens[2]
    def repoJavaVersion = tokens[3]
    def repoStyle = tokens[4]
    def repoBuildAction = tokens[5]
    def repoSkip = tokens[6]


    if ('true' == repoSkip) {
        return
    }

    def repoJobName = repoName.replaceAll('/', '_')

    boolean requiresJava = repoJavaVersion != null && !repoJavaVersion.equals("")


    println("creating job $repoJobName")

    job("ingest/$repoJobName") {

        if (requiresJava) {
            label('multi-jdk')
            jdk("java${repoJavaVersion}")
        }

        steps {
            scm {
                git {
                    remote {
                        url("https://${scmHost}/${repoName}")
                        branch(repoBranch)
                        credentials(scmCredentialsId)
                    }
                    extensions {
                        localBranch(repoBranch)
                    }
                }
            }
            wrappers {
                credentialsBinding {
                    usernamePassword('MODERNE_PUBLISH_USER', 'MODERNE_PUBLISH_PWD', publishCreds)
                    usernamePassword('AZURE_USER', 'AZURE_PWD', azureCreds)
                }
                configFiles {
                    file(mavenIngestSettingsXmlFileId) {
                        targetLocation(mavenIngestSettingsXmlRepoFile)
                    }
                }
            }
            def extraArgs = ''
            if (repoStyle != null && !repoStyle.equals("")) {
                extraArgs =  '--activeStyle ' + repoStyle
            }
            if (repoBuildAction != null && !repoBuildAction.equals("")) {
                extraArgs = extraArgs + ' --buildAction ' + repoBuildAction
            }
            if (requiresJava) {
                extraArgs = ' --mvnSettingsXml ' + mavenIngestSettingsXmlRepoFile
            }

            shell('curl --request GET https://pkgs.dev.azure.com/moderneinc/moderne/_packaging/moderne-cli/maven/v1/io/moderne/moderne-cli/'+ moderneCLIVersion +'/moderne-cli-'+ moderneCLIVersion +' --user ' + ${AZURE_USERNAME} + ':' + ${AZURE_PASSWORD} + ' >> mod && chmod u+x mod')
            shell('./mod publish --path ' + ${WORKSPACE} + ' --url ' + publishURL + ' --user ' + ${MODERNE_PUBLISH_USER} + ' --password '+ ${MODERNE_PUBLISH_PWD} + ' ' + extraArgs )
        }

        logRotator {
            daysToKeep(7)
        }
        triggers {
            cron(scheduling)
        }

        publishers {
            cleanWs()
        }
    }
    return
}
