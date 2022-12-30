## Purpose

One of the core steps for setting up your Moderne tenant is publishing Moderne abstract syntax trees (ASTs) to an artifact repository such as Artifactory or Nexus. 

While it is possible to [manually add a plugin to each repository](https://app.gitbook.com/o/-MEp_3EtccewzekKY8mZ/s/-MhFwm0iG8BFZKPYoFkH/how-to/integrating-private-code) to accomplish this, there are a few problems with that approach:
* It does not scale well in organizations with many repositories.
* It requires modifying the build processes of code, which may not be desired.
* The task which generates the ASTs takes time and memory. This, in turn, could negatively affect the building and shipping of code.

A much better and safer way to handle the creation of ASTs is to use Jenkins to mass produce the artifacts and for Moderne to ingest them nightly or at set intervals. 

This approach for creating ASTs at scheduled intervals is designed for use on a Jenkins installation with the [Job DSL](https://plugins.jenkins.io/job-dsl) plugin installed.

## Setup Instructions

### Copy this repository

If you use GitHub as your version control system, click the green "Use this template" button on this page to create a copy of this repository. This is distinct from a fork in that it can be copied into a private organization and it does not have an upstream link back to this repository.

<p align="center">
  <img width="284" alt="image" src="https://user-images.githubusercontent.com/1697736/189235703-0b7c1dcd-1e73-43f1-81d9-a39c617449c4.png">
</p>

If you **don't** use GitHub as your version control system, create a repository in your version control system, clone this repository, add a remote pointing to the repository in your version control system, then push the code into your version control.

Also, change [this line](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/add-repos.sh#L76) in [add-repos.sh](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/add-repos.sh) to point to your version control system (such as BitBucket or GitLab). Any git server will work.

### Configure `seed.groovy` for Artifactory or Nexus

The [seed.groovy](/seed.groovy) Job DSL script generates jobs that publish ASTs either to Artifactory or Nexus. They are mutually exclusive, so please follow one of the two configuration options:

#### If you want to publish ASTs to Artifactory

1. In [init.artifactory.gradle](/gradle/init.artifactory.gradle), look for the [publish task configuration](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/gradle/init.artifactory.gradle#L52-L57) that defines the Maven repository where artifacts will be published. Set this to any Artifactory repository.
2. If your repository requires cloning credentials, configure them [here](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/seed.groovy#L95) and [here](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/seed.groovy#L108-L110).

#### If you want to publish ASTs to Nexus
1. In [seed.groovy](/seed.groovy), update the [artifactRepositoryType variable](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/seed.groovy#L1) to have a value of `nexus`.
2. In [publish-ast.groovy](/publish-ast.groovy), update [repositoryRootUrl](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/publish-ast.groovy#L29) to point to the Nexus repository url that you want to publish ASTs to.
    >**Note**: On the initial run, the `publish-ast.groovy` script execution step will fail due to in-process script approval being required. Approve the script at `{JENKINS_URL}/scriptApproval/`.
3. Create credentials in Jenkins. The `id` should be `nexus` and the `type` should be `Username with password`. The credentials should be the ones you use to publish to Nexus. These credentials are bound [here](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/seed.groovy#L116-L119).

### Update ingestion time (optional)
In [seed.groovy](/seed.groovy), there is a [triggers section](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/seed.groovy#L103-L105) that specifies when the ingestion should happen. By default, it will run at around 4:00 am server time every day (Jenkins will pick what minute to run [based on load](https://stackoverflow.com/questions/26383778/spread-load-evenly-by-using-h-rather-than-5)).

If you'd like to choose a different time or have it run more often, you'll want to update [this line](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/seed.groovy#L103-L105).

### Configure Maven mirrors (optional)

1. Take note of the [mirrors section](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/maven/ingest-settings.xml#L2-L8) in [ingest-settings.xml](/maven/ingest-settings.xml) and consider whether this mirror will be DNS addressable in your corporate environment. We use this mirror (and many others) in the version of this process we use for the public Moderne tenant to reduce the load of dependency resolution on artifact repositories with rate limit policies (like Apache's). Inside of your environment, you likely have a different set of mirrors to accomplish the same.
2. If you define a mirror in `ingest-settings.xml` that requires credentials to resolve artifacts, uncomment [this line](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/seed.groovy#L37-L42) in `seed.groovy` and make sure there is a matching credential ID defined in Jenkins.

### Specify what repositories should be ingested

Each repository that is subject to nightly ingest is listed in `repos.csv`, along with calculated information about its build tooling and language level requirements. 

Whenever you want to add new repositories, please run the provided `add-repos.sh` script. This script takes in a `csv` file and results in additions to the [repos.csv](/repos.csv) file. This file serves as the source of repositories that the seed job will manage jobs for. 

Example of how to run the script with an input file called `input.csv`:

`./add-repos.sh -i input.csv`

The csv-file argument is expected to be a valid `csv` file, with optional headers:

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

## Troubleshooting

### `init-<nexus or artifactory>.gradle` changes
The `init-<nexus or artifactory>.gradle` file in this repository is imported into Jenkins. Any changes made to the file directly in Jenkins will be overwritten on each run of the seed job.
