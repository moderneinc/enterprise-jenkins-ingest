## Purpose

This process helps to mass produce Moderne abstract syntax trees (ASTs) for a large body of proprietary code without modifying the build processes of that code.

It is designed for use on a Jenkins installation with the [Job DSL](https://plugins.jenkins.io/job-dsl) plugin installed.

Each repository that is subject to nightly ingest is listed in `repos.csv`, along with calculated information about its build tooling and language level requirements.

## Modifications to use in your environment

1. Click the green "Use this template" button on this page to create a copy of this repository. This is distinct from a fork in that it can be copied into a private organization and does not have an upstream link back to this repository.

    <img width="284" alt="image" src="https://user-images.githubusercontent.com/1697736/189235703-0b7c1dcd-1e73-43f1-81d9-a39c617449c4.png">

2. Change this line in [add-repos.sh](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/add-repos.sh#L76) to point to your version control system. Note that it does not have to be a GitHub installation; any git server will work.

The `seed.groovy` Job DSL script generates jobs that publish ASTs either to Artifactory or Nexus.

### If you want to publish ASTs to Artifactory

1. In `init.gradle`, look for the publish task configuration [here](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/gradle/init.gradle#L52-L57) that defines the Maven repository where artifacts will be published. Set this to any Artifactory repository.
2. If your repository requires cloning credentials, configure them [here](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/seed.groovy#L95) and [here](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/seed.groovy#L108-L110).

### If you want to publish ASTs to Nexus
1. In `seed.groovy`, look for `artifactRepositoryType` [here](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/seed.groovy#L1). Change the value to 'nexus'.
2. In `publish-ast.groovy`, look for `repositoryRootUrl` [here](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/publish-ast.groovy#L34). Modify that to point to the Nexus3 repository url that you want to publish ASTs to.
3. On initial run, the `publish-ast.groovy` script execution step will fail due to in-process script approval being required. Approve the script at `{JENKINS_URL}/scriptApproval/`.
4. Create credentials in jenkins with id 'nexus', of kind "Username with password" with credentials to use when publishing to Nexus. These credentials are bound [here](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/seed.groovy#L112-L114).

### Configure mirrors (optional)

1. Take note of the mirrors section in `ingest-settings.xml` and consider whether this mirror will be DNS addressable in your corporate environment. We use this mirror (and many others) in the version of this process we use for the public Moderne tenant to reduce the load of dependency resolution on artifact repositories with rate limit policies (like Apache's). Inside of your environment, you likely have a different set of mirrors to accomplish the same.
2. If you define a mirror in `ingest-settings.xml` that requires credentials to resolve artifacts, uncomment [seed.groovy](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/seed.groovy#L37-L42) and make sure there is a matching credential ID defined in Jenkins.

## How to add repositories

Run the following Kotlin script to take an input `csv` file. The result will be additional lines
added to `repos.csv` in the root of this project, which serves as the source of repositories that the
seed job will manage jobs for.

`./add-repos.main.kts csv-file`

The csv-file argument is expected to be a valid `csv` file, with optional header:

`repoName,branch,javaVersion,style,buildTool,buildAction,skip,skipReason,artifactRepositoryType`

| Column                   | Required   | Notes                                                                                            |
|--------------------------|------------|--------------------------------------------------------------------------------------------------|
| repoName                 | Required   | Github repository with form `organization/name`, i.e. `google/guava`.                            |
| branch                   | Optional   | Github branch name to ingest.                                                                    |
| label                    | Optional   | Jenkins worker node label. Current supported values: {`java8`, `java11`}. Defaults to `java8`.   |
| style                    | Optional   | OpenRewrite style name to apply during ingest.                                                   |
| buildTool                | Optional   | Auto-detected if omitted. Current supported value: {`gradle`, `gradlew`, `maven`}.               |
| buildAction              | Optional   | Additional build tool tasks/targets to execute.                                                  |
| skip                     | Optional   | Use 'true' to omit ingest job creation for the CSV row.                                          |
| skipReason               | Optional   | Reason a job is set to skip                                                                      |
| artifactRepositoryType   | Optional   | Use 'nexus' or 'artifactory', determines AST publishing mechanism.                               |

## NOTE: `init.gradle` changes
The `init.gradle` file in this repository is imported into Jenkins. Any changes made to the file directly in Jenkins will be overwritten on each run of the seed job.
