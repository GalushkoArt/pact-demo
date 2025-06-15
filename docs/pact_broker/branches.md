---
title: Branches
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

From version 2.82.0 onwards, the Pact Broker supports repository branches as a first class concept. Previously, users
were recommended to use version [tags](tags) to represent the branch with which a particular pacticipant
version was associated. Adding explict support for branches allows the Pact Broker to provide simpler documentation,
better messaging and sensible defaults.

## Domain model

Branches in the Pact Broker are designed to model repository (git, svn etc) branches. A `branch` in the Pact Broker
belongs to a `pacticipant` (application). A `branch` may have many `pacticipant versions`, and a `pacticipant version`
may belong to many branches (but typically, it will belong to just one).

Remember that a `pacticipant version` in the Pact Broker should map 1:1 to a commit in your repository. To facilitate
this, the version number used to publish pacts and verification results should either [_be_ or
_contain_ the commit](../getting_started/versioning_in_the_pact_broker#guidelines).

## When are branches used?

Branches are used to identify which pacts a provider should verify
using consumer version selectors. Typically, the provider
should be configured to verify the pacts belonging to the main branch of each consumer. Branches are also used to
calculate the pending status of a pact and
identify work in progress pacts.

## Automatic branch creation from first tag

To assist in the migration from tags to branches, the Pact Broker from 2.82.0 supports the configuration option [use_first_tag_as_branch](https://github.com/pact-foundation/pact_broker/blob/master/docs/configuration.yml). When set to `true`, the first
tag applied to a pacticipant version that does not already have a branch will be inferred to be the branch. This feature
is only required to help transition from tags to branches during the rollout of branch support across the Pact clients.
Once your Pact clients all support and are configured with a branch, this can be disabled by setting
`use_first_tag_as_branch` to `false`.

## Pacticipant main branch property

From version 2.82.0 onwards, the `pacticipant` resource supports a `mainBranch` property. This property is used to
identify which versions to display first in the UI, and the branch for which a build should be run when the
`contract_requiring_verification_published` webhook is triggered.

### Automatic main branch detection

To assist in the migration from tags to branches, the main branch will be automatically set for a pacticipant if a
version is created with a branch or tag name matching one of `develop`, `main`, or `master`.

The main branch candidate names are [configurable](https://github.com/pact-foundation/pact_broker/blob/master/docs/configuration.yml). To
disable the automatic setting of the main branch, set [auto_detect_main_branch](https://github.com/pact-foundation/pact_broker/blob/master/docs/configuration.yml) configuration to `false`.

### Checking the main branch value

Use [`describe-pacticipant`]client_cli to check if the main branch is
configured.

```shell
pact-broker describe-pacticipant --name Foo
```

### Setting the main branch manually

To explicitly set the main branch of a pacticipant, use the Pact Broker Client [`create-or-update-pacticipant`]client_cli command.

```shell
pact-broker create-or-update-pacticipant --name Foo --main-branch dev
```

## Support

Support for publishing pacts and verification results with branches is currently (late 2022) being rolled out across the
Pact client libraries.

> We recommend using the Pact CLI for publishing pacts, some libraries will not provide support for publishing branches
> natively such as pact-net v4 and pact4s

* Pact Ruby - v1.59.0
* Ruby Dockerized pact-provider-verifier - v1.36.0
* Pact Python - v1.6.0
* Pact JS - v9.17.0 for verifying / For publishing see [Issue](https://github.com/pact-foundation/pact-js/issues/749)
* Pact Go - v1.6.6 [Issue](https://github.com/pact-foundation/pact-go/issues/184)
* Pact Rust - Pact Verifier Library v0.10.10 [Issue](https://github.com/pact-foundation/pact-reference/issues/151)
* Pact JVM - v4.1.39 / v4.3.12 / v4.4.0-beta.3 [Issue](https://github.com/pact-foundation/pact-jvm/issues/1454)
* Pact NET -  [v4.x](https://github.com/pact-foundation/pact-net/blob/master/docs/upgrading-to-4#provider-tests) for
  v3 spec pacts / TBC for ruby based core (v2 spec, v3
  pact-net) [Issue](https://github.com/pact-foundation/pact-net/issues/327),
* Pact Scala - TBC [Issue](https://github.com/ITV/scala-pact/issues/230)
* Pact4s - v0.2.0 [Issue](https://github.com/jbwheatley/pact4s/issues/89)
* Pact PHP - 7.1.0 [PR](https://github.com/pact-foundation/pact-php/pull/240)

## Migrating from tags to branches

Note the [Automatic branch creation](#automatic-branch-creation-from-first-tag) feature mentioned above.

* Upgrade to the latest version of your Pact client library (see the [support](#support) section above).
* Upgrade to Pact Broker version 2.82.0 or later.
* If your main branch is called something other than `develop`, `main` or `master`, set the main
  branch [manually](#setting-the-main-branch-manually)
* Set the branch property when publishing pacts
  and/or verification results.
* In the provider, update the consumer version selectors from `{ "tag": "<branch_name>"}` to
  `{ "branch": "<branch_name>"}`

## FAQ

### We never use feature branches - do I need to set the branch properties?

Yes. Even if you only ever use one branch, it is recommended to set the branch property when publishing pacts and
verification results, and to set the pacticipant's `mainBranch` property. This will allow the Pact Broker to distinguish
between a poorly configured Pact Broker (where none of those are populated) and a trunk based workflow.