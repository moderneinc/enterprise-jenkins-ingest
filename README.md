## Purpose

One of the core steps for setting up your Moderne tenant is publishing Moderne [lossless semantic trees](https://docs.moderne.io/concepts/lossless-semantic-trees) (LSTs) to an artifact repository such as Artifactory or Nexus. 

While it is possible to [manually add a plugin to each repository](https://docs.moderne.io/how-to/integrating-private-code) to accomplish this, there are a few problems with that approach:
* It does not scale well in organizations with many repositories.
* It requires modifying the build processes of code, which may not be desired.
* The task which generates the LSTs takes time and memory. This, in turn, could negatively affect the building and shipping of code.

A much better and safer way to handle the creation of LSTs is to use Jenkins to mass produce the artifacts and for Moderne to ingest them nightly or at set intervals. 

This approach for creating LSTs at scheduled intervals is designed for use on a Jenkins installation with the [Job DSL](https://plugins.jenkins.io/job-dsl) plugin installed.

## Jenkins Setup Instructions

- Install the [Job DSL Plugin](https://plugins.jenkins.io/job-dsl)
- Install the [Config File Provider Plugin](https://plugins.jenkins.io/config-file-provider)
- Install Maven and/or Gradle if they are needed to build your repositories.

### Copy this repository

If you use GitHub as your version control system, click the green "Use this template" button on this page to create a copy of this repository. This is distinct from a fork in that it can be copied into a private organization and it does not have an upstream link back to this repository.

<p align="center">
  <img width="284" alt="image" src="https://user-images.githubusercontent.com/1697736/189235703-0b7c1dcd-1e73-43f1-81d9-a39c617449c4.png">
</p>

If you **don't** use GitHub as your version control system, create a repository in your version control system, clone this repository, add a remote pointing to the repository in your version control system, then push the code into your version control.

Also, change [this line](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/add-repos.sh#L76) in [add-repos.sh](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/add-repos.sh) to point to your version control system (such as BitBucket or GitLab). Any git server will work.

### Configure `seed.groovy` with your developer tools and credentials

The [seed.groovy](/seed.groovy) Job DSL script generates jobs that publish LSTs using a container image. Open the file, 
and define the value for the following parameters:

1. `publishURL` [Required]: The URL to publish the LSTs. This needs to be a Maven repository
2. `publishCreds`: The Jenkins credentials ID that reference your user/password for your Maven repository. By default is `artifactory`
3. `scmCredentialsId`: The Jenkins credentials ID to clone the repo. By default is `cloning-creds`
4. `containerIngestorVersion`: By default is `latest`.
6. `scheduling`: that specifies when the ingestion should happen. By default, it will run at around 4:00 am server time every day (Jenkins will pick what minute to run [based on load](https://stackoverflow.com/questions/26383778/spread-load-evenly-by-using-h-rather-than-5))

### Specify what repositories should be ingested

Each repository that is subject to nightly ingest is listed in `repos.csv`, along with calculated information 
about its build tooling and language level requirements. 

Whenever you want to add new repositories, please run the provided `add-repos.sh` script. This script takes in a `csv` file and results in additions to the [repos.csv](/repos.csv) file. This file serves as the source of repositories that the seed job will manage jobs for. 

Example of how to run the script with an input file called `input.csv`:

`./add-repos.sh -i input.csv`

The csv-file argument is expected to be a valid `csv` file, with optional headers:

`scmHost,repoName,branch,javaVersion,repoStyle,repoBuildAction,skip`

| Column          | Required | Notes                                                                             |
|-----------------|----------|-----------------------------------------------------------------------------------|
| scmHost         | Optional | SCM Host. By default `github.com`.                                                |
| repoName        | Required | Repository Slug with form `organization/name`, i.e. `google/guava`.               |
| branch          | Optional | Github branch name to ingest.                                                     |
| Java Version    | Optional | Java version. Current supported values: {`8`, `11`, `17`}. Defaults to `8`.       |
| repoStyle       | Optional | OpenRewrite style name to apply during ingest.                                    |
| repoBuildAction | Optional | Additional build tool tasks/targets to execute first for Maven and Gradle builds. |
| skip            | Optional | Repo to skip                                                                      |
