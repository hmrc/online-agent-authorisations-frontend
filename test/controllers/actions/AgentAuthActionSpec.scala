/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers.actions

import base.SpecBase
import config.FrontendAppConfig
import controllers.routes
import models.AgentService
import play.api.mvc.{Action, AnyContent, BodyParsers, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class AgentAuthActionSpec extends SpecBase {

  private type AuthRetrievals =
    Option[String] ~ Option[AffinityGroup] ~ Option[String] ~ Option[Credentials] ~ Option[String] ~ Enrolments

  class Harness(authAction: IdentifierAction) {
    def onPageLoad(): Action[AnyContent] = authAction { request =>
      val services = request.activatedServices.map(s => s"${s.code}=${request.agentReference(s).getOrElse("")}").mkString(",")
      Results.Ok(s"${request.agentCode}|${request.credentialId}|${request.email.getOrElse("")}|$services")
    }
  }

  private val ActivatedState    = "Activated"
  private val NotYetActivated   = "NotYetActivated"

  private def enrolment(key: String, identifierName: String, identifierValue: String, state: String = ActivatedState): Enrolment =
    Enrolment(key, Seq(EnrolmentIdentifier(identifierName, identifierValue)), state)

  private val saAgent    = enrolment("IR-SA-AGENT", "IRAgentReference", "SA0001")
  private val ctAgent    = enrolment("IR-CT-AGENT", "IRAgentReference", "CT0001")
  private val payeAgent  = enrolment("IR-PAYE-AGENT", "IRAgentReference", "PAYE0001")
  private val hmceAgent  = enrolment("HMCE-VAT-AGNT", "AgentRefNo", "VAT0001")
  private val novrnAgent = enrolment("HMRC-NOVRN-AGNT", "VATAgentRefNo", "NOVRN001")
  private val mgdAgent   = enrolment("HMRC-MGD-AGNT", "HMRCMGDAGENTREF", "MGD0001")

  private val allSixActivated = Set(saAgent, ctAgent, payeAgent, hmceAgent, novrnAgent, mgdAgent)

  private def authRetrievals(
                              internalId: Option[String]          = Some("internalId"),
                              affinityGroup: Option[AffinityGroup] = Some(AffinityGroup.Agent),
                              agentCode: Option[String]           = Some("AGENTCODE1"),
                              credentials: Option[Credentials]    = Some(Credentials("cred-id", "GovernmentGateway")),
                              email: Option[String]               = Some("agent@example.com"),
                              enrolments: Set[Enrolment]          = Set(saAgent)
                            ): AuthRetrievals =
    new ~(new ~(new ~(new ~(new ~(internalId, affinityGroup), agentCode), credentials), email), Enrolments(enrolments))

  private def runAction(retrievals: AuthRetrievals, request: FakeRequest[AnyContent] = FakeRequest()) = {
    val application = applicationBuilder(userAnswers = None).build()

    running(application) {
      val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
      val appConfig   = application.injector.instanceOf[FrontendAppConfig]

      val authAction = new AuthenticatedIdentifierAction(new FakeAuthConnector(retrievals), appConfig, bodyParsers)
      new Harness(authAction).onPageLoad()(request)
    }
  }

  "Agent Auth Action" - {

    "when the user is an agent with an agent code and an activated enrolment" - {

      Seq(
        saAgent    -> "SA=SA0001",
        ctAgent    -> "CT=CT0001",
        payeAgent  -> "PAYE=PAYE0001,CIS=PAYE0001,PAYERS=PAYE0001",
        hmceAgent  -> "UKVAT=VAT0001,EUVAT=VAT0001,NOVA=VAT0001,MOSS=VAT0001,MOSSNU=VAT0001",
        novrnAgent -> "UKVAT=NOVRN001,EUVAT=NOVRN001,NOVA=NOVRN001,MOSS=NOVRN001,MOSSNU=NOVRN001",
        mgdAgent   -> "MGD=MGD0001"
      ).foreach { case (agentEnrolment, expectedServices) =>

        s"must allow access with only ${agentEnrolment.key} activated" in {

          val result = runAction(authRetrievals(enrolments = Set(agentEnrolment)))

          status(result) mustBe OK
          contentAsString(result) mustBe s"AGENTCODE1|cred-id|agent@example.com|$expectedServices"
        }
      }

      "must give every service when all six enrolments are activated" in {

        val result = runAction(authRetrievals(enrolments = allSixActivated))

        status(result) mustBe OK
        contentAsString(result).split('|').last.split(',').map(_.takeWhile(_ != '=')).toSeq mustBe
          AgentService.values.map(_.code).toSeq
      }

      "must use the HMCE-VAT-AGNT reference over HMRC-NOVRN-AGNT when both are activated" in {

        val result = runAction(authRetrievals(enrolments = Set(hmceAgent, novrnAgent)))

        status(result) mustBe OK
        contentAsString(result) must endWith("UKVAT=VAT0001,EUVAT=VAT0001,NOVA=VAT0001,MOSS=VAT0001,MOSSNU=VAT0001")
      }

      "must use the HMRC-NOVRN-AGNT reference when HMCE-VAT-AGNT is not yet activated" in {

        val result = runAction(authRetrievals(enrolments = Set(hmceAgent.copy(state = NotYetActivated), novrnAgent)))

        status(result) mustBe OK
        contentAsString(result) must endWith("UKVAT=NOVRN001,EUVAT=NOVRN001,NOVA=NOVRN001,MOSS=NOVRN001,MOSSNU=NOVRN001")
      }

      "must only include activated services when some enrolments are not yet activated" in {

        val result = runAction(authRetrievals(enrolments = Set(saAgent, ctAgent.copy(state = NotYetActivated))))

        status(result) mustBe OK
        contentAsString(result) must endWith("|SA=SA0001")
      }

      "must allow access when the agent has no email address" in {

        val result = runAction(authRetrievals(email = None))

        status(result) mustBe OK
        contentAsString(result) mustBe "AGENTCODE1|cred-id||SA=SA0001"
      }
    }

    "when the user is not an agent" - {

      Seq(AffinityGroup.Organisation, AffinityGroup.Individual).foreach { affinityGroup =>

        s"must redirect an $affinityGroup to the unauthorised page" in {

          val result = runAction(authRetrievals(affinityGroup = Some(affinityGroup)))

          status(result) mustBe SEE_OTHER
          redirectLocation(result).value mustBe routes.UnauthorisedController.onPageLoad().url
        }
      }

      "must redirect a user with no affinity group to the unauthorised page" in {

        val result = runAction(authRetrievals(affinityGroup = None))

        status(result) mustBe SEE_OTHER
        redirectLocation(result).value mustBe routes.UnauthorisedController.onPageLoad().url
      }
    }

    "when the agent has no agent code" - {

      "must redirect to the unauthorised page when the agent code is missing" in {

        val result = runAction(authRetrievals(agentCode = None))

        status(result) mustBe SEE_OTHER
        redirectLocation(result).value mustBe routes.UnauthorisedController.onPageLoad().url
      }

      "must redirect to the unauthorised page when the agent code is blank" in {

        val result = runAction(authRetrievals(agentCode = Some("")))

        status(result) mustBe SEE_OTHER
        redirectLocation(result).value mustBe routes.UnauthorisedController.onPageLoad().url
      }
    }

    "when the agent has no credentials" - {

      "must redirect to the unauthorised page" in {

        val result = runAction(authRetrievals(credentials = None))

        status(result) mustBe SEE_OTHER
        redirectLocation(result).value mustBe routes.UnauthorisedController.onPageLoad().url
      }
    }

    "when the agent has no activated enrolment" - {

      "must redirect to the activate enrolment page when one enrolment is not yet activated" in {

        val result = runAction(authRetrievals(enrolments = Set(saAgent.copy(state = NotYetActivated))))

        status(result) mustBe SEE_OTHER
        redirectLocation(result).value mustBe routes.ActivateAgentEnrolmentController.onPageLoad().url
      }

      "must redirect to the activate enrolment page when all six are not yet activated" in {

        val result = runAction(authRetrievals(enrolments = allSixActivated.map(_.copy(state = NotYetActivated))))

        status(result) mustBe SEE_OTHER
        redirectLocation(result).value mustBe routes.ActivateAgentEnrolmentController.onPageLoad().url
      }

      "must redirect to the unauthorised page when the agent has no enrolments" in {

        val result = runAction(authRetrievals(enrolments = Set.empty))

        status(result) mustBe SEE_OTHER
        redirectLocation(result).value mustBe routes.UnauthorisedController.onPageLoad().url
      }

      "must redirect to the unauthorised page when the agent only has non-OAA enrolments" in {

        val result = runAction(authRetrievals(enrolments = Set(enrolment("IR-SA", "UTR", "1234567890"))))

        status(result) mustBe SEE_OTHER
        redirectLocation(result).value mustBe routes.UnauthorisedController.onPageLoad().url
      }

      "must redirect to the unauthorised page when the only activated enrolment has no identifier" in {

        val result = runAction(authRetrievals(enrolments = Set(Enrolment("IR-SA-AGENT", Seq.empty, ActivatedState))))

        status(result) mustBe SEE_OTHER
        redirectLocation(result).value mustBe routes.UnauthorisedController.onPageLoad().url
      }
    }

    "when auth returns no internal id" - {

      "must fail with an UnauthorizedException" in {

        val result = runAction(authRetrievals(internalId = None))

        result.failed.futureValue mustBe an[UnauthorizedException]
      }
    }

    "when the user is not signed in" - {

      "must redirect to sign in and come back to the page they asked for" in {

        val application = applicationBuilder(userAnswers = None).build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          val appConfig   = application.injector.instanceOf[FrontendAppConfig]

          val authAction = new AuthenticatedIdentifierAction(new FakeFailingAuthConnector(new MissingBearerToken), appConfig, bodyParsers)
          val result     = new Harness(authAction).onPageLoad()(FakeRequest(GET, "/online-agent-authorisations-frontend/some-page"))

          status(result) mustBe SEE_OTHER
          redirectLocation(result).value must startWith(appConfig.loginUrl)
          redirectLocation(result).value must include(
            java.net.URLEncoder.encode(s"${appConfig.host}/online-agent-authorisations-frontend/some-page", "UTF-8")
          )
        }
      }
    }
  }
}

class FakeAuthConnector[T](retrievals: T) extends AuthConnector {
  val serviceUrl: String = ""

  override def authorise[A](predicate: Predicate, retrieval: Retrieval[A])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] =
    Future.successful(retrievals.asInstanceOf[A])
}
