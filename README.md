# online-agent-authorisations-frontend

This is the frontend for Online Agent Authorisations (OAA). Agents use it to ask their clients for authorisation to deal with their tax for a service (SA, CT, PAYE, CIS, ERS, EU VAT, NOVA, MOSS, MOSS NU and MGD).

OAA is used by agents only. Every page sits behind an agent auth action, which checks the user before the page loads (see [Authentication](#authentication)).

## Running the service
Before starting, you will need to have [service-manager](https://github.com/hmrc/service-manager) installed/configured

### Dependencies
All dependencies can be found in [AppDependencies.scala](project/AppDependencies.scala)

### Running locally:
Service Manager:
- Start dependent services `sm2 --start OAA_ALL`
- Stop this service `sm2 --stop ONLINE_AGENT_AUTHORISATIONS_FRONTEND`
- Start the server locally on `port 20110` with `sbt run`

The service runs at http://localhost:20110/online-agent-authorisations-frontend

### Signing in locally:
- Sign in through auth-login-stub at http://localhost:9949/auth-login-stub/gg-sign-in
- Set the Redirect URL to `http://localhost:20110/online-agent-authorisations-frontend`
- Set the Affinity Group to `Agent` and add at least one of the agent enrolments below, with state `Activated`
- The OAA Tampermonkey script in the stub repo fills this in for you with ready-made agent profiles

Note: auth-login-stub sets the agent code to `123456789` when the Agent code field is left blank, so you can't sign in as an agent with no agent code through the stub. That case is covered by the unit tests.

### Testing:
- Run unit tests: `sbt test`
- Run integration tests: `sbt it/test`
- To run all tests and coverage: `sbt clean compile coverage test it/test coverageOff coverageReport`
- To run the service in test-only mode: `sbt run -Dapplication.router=testOnlyDoNotUseInAppConf.Routes`

The build uses `-Xfatal-warnings`, so any compiler warning (for example an unused import) fails the build.

## Authentication

All pages use `IdentifierAction` ([AuthenticatedIdentifierAction](app/controllers/actions/IdentifierAction.scala)). The agent code comes from auth, not the URL.

The action checks, in order:

| Check | If it fails |
|---|---|
| The user is signed in | Redirect to sign in, then back to the page they asked for |
| The affinity group is `Agent` | Unauthorised page |
| The user has an agent code | Unauthorised page |
| The user has at least one OAA agent enrolment with state `Activated` | Activate enrolment page if they hold OAA enrolments but none are activated; Unauthorised page if they have none |

An agent with some enrolments activated and some not gets in, with only the activated services.

### Agent enrolments

| Enrolment | Identifier | Services |
|---|---|---|
| IR-SA-AGENT | IRAgentReference | SA |
| IR-CT-AGENT | IRAgentReference | CT |
| IR-PAYE-AGENT | IRAgentReference | PAYE, CIS, ERS |
| HMCE-VAT-AGNT | AgentRefNo | UK VAT, EU VAT, NOVA, MOSS, MOSS NU |
| HMRC-NOVRN-AGNT | VATAgentRefNo | UK VAT, EU VAT, NOVA, MOSS, MOSS NU (only used if HMCE-VAT-AGNT is not activated) |
| HMRC-MGD-AGNT | HMRCMGDAGENTREF | MGD |

The mapping lives in [AgentEnrolmentService.scala](app/models/AgentEnrolmentService.scala).

### What the request carries

Every request that passes the auth action (`IdentifierRequest`) has:

| Field | Used for |
|---|---|
| `agentCode` | Sent to the DB as AGENT_ID, AGENT_CODE and GATEWAY_AGENT_ID |
| `credentialId` | Sent as USER_ID on tracking messages |
| `email` | Pre-fills the email address page |
| `activatedServices` | Decides which services are shown and which pages the agent can use |
| `agentReference(service)` | Sent as IR_AGENT_REF when a request is saved, and checked against the request when it is loaded |

### Service codes

Always use `AgentService.code` when sending a service to oaa-exb or comparing it with a request's service, never `toString` or the enum name. The codes match the legacy database: ERS is stored as `PAYERS`.

- `AgentService.fromCode(code)` finds a service by its legacy code (case-sensitive).
- `AgentService.requestable` lists the services an agent can make a new request for. It excludes UK VAT: agents can't make new UK VAT requests, but old ones still appear on the unsubmitted and pending lists.

## Adding New Pages

### Folder Structure

Use domain-based organisation. Each new page should be placed in the appropriate domain folder:

```
app/
├── controllers/[section]/               # e.g. controllers/request
├── models/[section]/                    # e.g. models/request
├── views/[section]/                     # e.g. views/request
├── forms/[section]/                     # e.g. forms/request
├── pages/[section]/                     # e.g. pages/request
└── viewmodels/checkAnswers/[section]/   # e.g. viewmodels/checkAnswers/request
```

```
test/
├── controllers/[section]/   # e.g. controllers/request
├── models/[section]/        # e.g. models/request
├── forms/[section]/         # e.g. forms/request
└── views/[section]/         # e.g. views/request
```

Pages are created with the scaffolding (`sbt g8Scaffold <template>`, then `./migrate.sh`). Use the `contentPage` template for pages with no form.

### Example routes and messages

```GET  /request/select-service  controllers.request.SelectServiceController.onPageLoad(mode: Mode = NormalMode)```

Message key (messages.en):

```request.selectService.heading = Which service do you want to request authorisation for?```

Add the Welsh message to `messages.cy` as well.

### License

This code is open source software licensed under
the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).
