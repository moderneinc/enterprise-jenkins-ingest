def workspaceDir = new File(__FILE__).getParentFile()

def publishURL = 'https://artifactory.acme.com/artifactory/moderne-ingest'
def publishCreds = 'artifactory'
def scmCredentialsId = 'cloning-creds'
def containerIngestorVersion= 'latest'
def scheduling='H 4 * * *'

folder('ingest') {
    displayName('Ingest Jobs')
}

new File(workspaceDir, 'repos.csv').splitEachLine(',') { tokens ->
    if (tokens[0].startsWith('repoName')) {
        return
    }
    def scmHost = tokens[0]
    def repoName = tokens[1]
    def repoBranch = tokens[2]
    def repoJavaVersion = tokens[3]

    if ('true' == repoSkip) {
        return
    }

    def repoJobName = repoName.replaceAll('/', '_')

    println("creating job $repoJobName")

    job("ingest/$repoJobName") {

        git url:"https://${scmHost}/${repoName}", branch:repoBranch, credentialsId:scmCredentialsId

        withCredentials([usernamePassword(credentialsId: publishCreds, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {

            sh 'docker run -v ' + pwd() + ':/repository -e JAVA_VERSION=1.'+ repoJavaVersion +' -e PUBLISH_URL='+publishURL+' -e PUBLISH_USER=' + USERNAME + ' -e PUBLISH_PWD=' + PASSWORD + ' moderne/moderne-ingestor:' + containerIngestorVersion

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
