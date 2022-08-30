## Purpose

This process helps to mass produce Moderne abstract syntax trees (ASTs) for a large body of proprietary code without modifying the build processes of that code.

It is designed for use on a Jenkins installation with the [Job DSL](https://plugins.jenkins.io/job-dsl) plugin installed.

Each repository that is subject to nightly ingest is listed in `repos.csv`, along with calculated information about its build tooling and language level requirements.

## Modifications to use in your environment

1. Click the green "Use this template" button on this page to create a copy of this repository. This is distinct from a fork in that it can be copied into a private organization and does not have an upstream link back to this repository.
2. Change this line in [add-repos.sh](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/add-repos.sh#L76) to point to your version control system. Note that it does not have to be a GitHub installation; any git server will work.
3. In `init.gradle`, look for the publish task configuration [here](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/gradle/init.gradle#L52-L57) that defines the Maven repository where artifacts will be published. Set this to any Artifactory repository.
4. If your repository requires cloning credentials, configure them [here](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/seed.groovy#L93) and [here](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/seed.groovy#L106-L108).

Optionally:

1. Take note of the mirrors section in `ingest-settings.xml` and consider whether this mirror will be DNS addressable in your corporate environment. We use this mirror (and many others) in the version of this process we use for the public Moderne tenant to reduce the load of dependency resolution on artifact repositories with rate limit policies (like Apache's). Inside of your environment, you likely have a different set of mirrors to accomplish the same.
2. If you define a mirror in `ingest-settings.xml` that requires credentials to resolve artifacts, uncomment [seed.groovy](https://github.com/moderneinc/enterprise-jenkins-ingest/blob/main/seed.groovy#L35-L40) and make sure there is a matching credential ID defined in Jenkins.

## How to add repositories

Run the following Kotlin script to take an input `csv` file. The result will be additional lines
added to `repos.csv` in the root of this project, which serves as the source of repositories that the
seed job will manage jobs for.

`./add-repos.main.kts csv-file`

The csv-file argument is expected to be a valid `csv` file, with optional header:

`repoName, branch, label, style, buildTool`

| Column | Required | Notes |
|----|----|----|
|repoName | Required | Github repository with form `organization/name`, i.e. `google/guava`. |
|branch | Optional | Github branch name to ingest. |
|label | Optional | Jenkins worker node label. Current supported values: {`java8`, `java11`}. Defaults to `java8`. |
|style | Optional | OpenRewrite style name to apply during ingest. |
|buildTool | Optional | Auto-detected if omitted. Current supported value: {`gradle`, `gradlew`, `maven`}. |

## NOTE: `init.gradle` changes
The `init.gradle` file in this repository is imported into Jenkins. Any changes made to the file directly in Jenkins will be overwritten on each run of the seed job.
