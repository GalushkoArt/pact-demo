---
title: CI/CD Setup Guide
sidebar_label: Introduction
---

_"The steps for reaching Pact Nirvana"_

This is a technical guide for developers and testers who want to use Pact to implement consumer driven contract testing as part of your ongoing CI/CD workflow.

By the end of the guide, you will understand how to create a release pipeline that allows you to independently deploy any application with the confidence that it will work correctly with the other applications in its environment - without having to run a suite of end to end tests.

This guide focuses on the scenario where the consumer and provider are both being deployed to an environment rather than released to customers (e.g. a mobile app). That workflow is slightly different, and that guidance will be coming soon.

### How to use this document

Each integration is different. Each organisation has different history and culture, and each team may have different processes for development, testing, and deployment. Each of these differences affect the best choices for Pact workflow.

However, there are many similarities in the steps necessary on the journey to a full-featured and effective Pact setup \(_"Pact Nirvana"_\). This document describes those steps.

Feel free to pick and choose the steps that apply best to your team. You may implement only the first few steps described below, and just use Pact as a precursor to your standard integration tests; or you may throw away your integration tests altogether and reach "Pact Nirvana".

This guide doesn't go into the details of how to write and run consumer tests or provider verification tests. Here we are laying out a high-level strategy for how you can get Pact set up and operational.  Each of the levels below gets you a step towards having a fully automated process of verifying contracts as part of CI/CD with no need for an integration environment.

As Pact has been implemented in many different languages, this document will outline the theory for each step. You will need to consult the documentation of your chosen language to learn the relevant syntax for each task. See the [implementation guides](/implementation_guides/cli) section for more information.

### What are the steps for reaching Pact Nirvana?

1. [Get Prepared - learn about pact](pact_nirvana/step_1)
2. [Talk: get team alignment](pact_nirvana/step_2)
3. Bronze level: get a single test working manually
4. Silver level: manually integrate with Pact Broker
5. Gold level - integrate with your PR pipelines
6. Platinum level: Add can-i-deploy with branch tag to PR pipelines
7. Diamond level - Add Pact to your deploy pipelines
8. Honors Course - To come in future...
    1. Add provider state
    2. Working with Feature Flags

Click below to expand and see each a diagram representing each level


<details >
  <summary>Bronze diagram</summary>

```plantuml
@startuml
left to right direction
actor "Consumer Test" as CT
actor "Provider Verification by Local File" as PV

CT --> [Pact File] : write
[Pact File] --> PV : read
@enduml
```
</details>
<details >
  <summary>Silver diagram</summary>

```plantuml
@startuml
left to right direction
actor "Consumer Test" as CT
database "Pact Broker" as PB
actor "Provider Verification by URL" as PV

CT --> PB : publish
PB --> PV : retrieve pact by URL
@enduml
```
</details>
<details >
  <summary>Gold diagram</summary>

```plantuml
@startuml
actor Consumer
actor Provider
database Broker

Consumer -> Broker : publish pact [feat 123]
note left of Consumer : PR validation pipeline

Consumer -> Broker : publish pact [main]
note left of Consumer : Commit/main pipeline
note right of Provider : PR validation pipeline

Provider -> Broker : verify against consumer's main\n+ deployed versions
Provider -> Broker : publish results (provider version + branch)
@enduml
```
</details>
<details >
  <summary>Platinum diagram</summary>

```plantuml
@startuml
participant Consumer
participant Broker
participant Verifier

note left of Consumer: PR validation pipeline

Consumer ->> Broker : publish pact with branch [feat abc]
alt pact has changed, verification does not exist
    Broker ->> Verifier : {webhook} run verification for pact version 123 [feat abc]
    Consumer ->> Broker : can-i-deploy --to-environment dev
    Consumer ->> Consumer : wait for results...
    Verifier ->> Verifier : pull provider from main branch
    Verifier ->> Broker : get pact version 123
    Verifier ->> Verifier : verify against pact

    alt verification passed
        Broker ->> Consumer : Yes
    else verification failed
        Broker ->> Consumer : NO
    end
else no change to pact, verification exists
    ' TODO: how do we do the can-i-merge check now with branches?
    '       need https://github.com/pact-foundation/pact_broker-client/issues/138
    Consumer ->> Broker : can-i-deploy --to-environment dev

    alt verification passed
        Broker ->> Consumer : Yes
    else verification failed
        Broker ->> Consumer : NO
    end
end
@enduml

```
</details>
<details >
  <summary>Diamond diagram</summary>


```plantuml
@startuml
actor Consumer
actor Verifier
database Broker

note left of Consumer : PR validation pipeline
Consumer -> Broker : publish pact [feat abc]

alt pact changed & no verification
    Broker -> Verifier : webhook to run verification
    Consumer -> Broker : can-i-deploy --to-environment dev
    Consumer -> Consumer : wait for results...
    Verifier -> Verifier : pull provider from main
    Verifier -> Broker : get pact [123]
    Verifier -> Verifier : verify against pact

    alt passed
        Broker -> Consumer : Yes
    else failed
        Broker -> Consumer : NO
    end
else pact unchanged & verified
    Consumer -> Broker : can-i-deploy --to-environment dev

    alt passed
        Broker -> Consumer : Yes
    else failed
        Broker -> Consumer : NO
    end
end
@enduml
```
</details>

<details >
  <summary>Diamond with release branch diagram</summary>

```plantuml
@startuml
actor Provider
database Broker

note left of Provider : PR validation pipeline
Provider -> Broker : verify pacts (mainBranch + deployedOrReleased)
Provider -> Broker : publish results, tag [feat abc]
Provider -> Broker : can-i-deploy --to-environment [staging]

note left of Provider : Main branch commit pipeline
Provider -> Broker : verify pacts (mainBranch + deployedOrReleased)
Provider -> Broker : publish results, tag [main]
Provider -> Broker : can-i-deploy --to-environment [staging]
Provider -> Provider : deploy to [staging]
Provider -> Broker : record-deployment [staging]

note left of Provider : Release branch pipeline
Provider -> Broker : can-i-deploy --to-environment [preprod]
Provider -> Broker : can-i-deploy --to-environment [prod]
Provider -> Provider : cut release branch
Provider -> Provider : deploy to [preprod]
Provider -> Provider : run [preprod] tests
Provider -> Broker : can-i-deploy --to-environment [prod]
Provider -> Provider : deploy to [prod]
Provider -> Broker : record-deployment [prod]
@enduml
```
</details>