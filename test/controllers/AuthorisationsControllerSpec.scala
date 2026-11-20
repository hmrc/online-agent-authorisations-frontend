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

package controllers

import base.SpecBase
import connectors.OnlineAgentAuthorisationsConnector
import controllers.actions.{FakeIdentifierAction, IdentifierAction}
import handlers.ErrorHandler
import models.requests.{AgentDetails, IdentifierRequest}
import models.{AgentListCounts, UserAnswers}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.{never, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.*
import play.api.mvc.Results.Redirect
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import uk.gov.hmrc.http.UpstreamErrorResponse
import views.html.AuthorisationsView

import scala.concurrent.{ExecutionContext, Future}

class AuthorisationsControllerSpec extends SpecBase with MockitoSugar {

  private lazy val authorisationsRoute: String = routes.AuthorisationsController.onPageLoad().url

  private val counts = AgentListCounts(unsubmitted = 2, submitted = 3)

  // The page has no form, so the fake actions don't need to read a body
  private val ignoreBody: BodyParser[AnyContent] = BodyParsers.utils.ignore[AnyContent](AnyContentAsEmpty)

  // An auth action that lets a given agent through
  private class AgentIdentifierAction(agent: AgentDetails) extends IdentifierAction {
    override def invokeBlock[A](request: Request[A], block: IdentifierRequest[A] => Future[Result]): Future[Result] =
      block(IdentifierRequest(request, "id", agent))
    override def parser: BodyParser[AnyContent]               = ignoreBody
    override protected def executionContext: ExecutionContext = ExecutionContext.global
  }

  // An auth action that rejects everyone, as the real one does for a non-agent
  private class RejectingIdentifierAction extends IdentifierAction {
    override def invokeBlock[A](request: Request[A], block: IdentifierRequest[A] => Future[Result]): Future[Result] =
      Future.successful(Redirect(routes.UnauthorisedController.onPageLoad()))
    override def parser: BodyParser[AnyContent]               = ignoreBody
    override protected def executionContext: ExecutionContext = ExecutionContext.global
  }

  private def mocks(
                     countsResult: Future[AgentListCounts] = Future.successful(counts),
                     userAnswers: Option[UserAnswers]      = None
                   ): (OnlineAgentAuthorisationsConnector, SessionRepository) = {
    val connector         = mock[OnlineAgentAuthorisationsConnector]
    val sessionRepository = mock[SessionRepository]

    when(connector.countAgentLists(any())(any())).thenReturn(countsResult)
    when(sessionRepository.get(any())).thenReturn(Future.successful(userAnswers))
    when(sessionRepository.set(any())).thenReturn(Future.successful(true))

    (connector, sessionRepository)
  }

  // Built directly (not via SpecBase) so each test can choose its own IdentifierAction
  private def applicationWith(
                               connector: OnlineAgentAuthorisationsConnector,
                               sessionRepository: SessionRepository,
                               identifierAction: IdentifierAction
                             ): Application =
    new GuiceApplicationBuilder()
      .overrides(
        bind[IdentifierAction].toInstance(identifierAction),
        bind[OnlineAgentAuthorisationsConnector].toInstance(connector),
        bind[SessionRepository].toInstance(sessionRepository)
      )
      .build()

  private def application(
                           connector: OnlineAgentAuthorisationsConnector,
                           sessionRepository: SessionRepository,
                           agent: AgentDetails = FakeIdentifierAction.agent
                         ): Application =
    applicationWith(connector, sessionRepository, new AgentIdentifierAction(agent))

  "Authorisations Controller" - {

    "must return OK and the correct view for a GET" in {

      val (connector, sessionRepository) = mocks()
      val app                            = application(connector, sessionRepository)

      running(app) {
        val request = FakeRequest(GET, authorisationsRoute)

        val result = route(app, request).value

        val view = app.injector.instanceOf[AuthorisationsView]

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(counts, FakeIdentifierAction.agent)(request, messages(app)).toString
      }
    }

    "must set Cache-Control to no-store" in {

      val (connector, sessionRepository) = mocks()
      val app                            = application(connector, sessionRepository)

      running(app) {
        val result = route(app, FakeRequest(GET, authorisationsRoute)).value

        header(CACHE_CONTROL, result).value mustEqual "no-store"
      }
    }

    "must ask the backend for the counts using the agent code from auth" in {

      val (connector, sessionRepository) = mocks()
      val app                            = application(connector, sessionRepository)

      running(app) {
        status(route(app, FakeRequest(GET, authorisationsRoute)).value) mustEqual OK

        verify(connector).countAgentLists(meq(FakeIdentifierAction.agent.agentCode))(any())
      }
    }

    "must show the counts on the unsubmitted and waiting for a code links" in {

      val (connector, sessionRepository) = mocks()
      val app                            = application(connector, sessionRepository)

      running(app) {
        val content = contentAsString(route(app, FakeRequest(GET, authorisationsRoute)).value)

        content must include("Unsubmitted requests (2)")
        content must include("Waiting for an authorisation code (3)")
      }
    }

    "must show both counts as 0 when the backend returns 0 and 0" in {

      val (connector, sessionRepository) = mocks(countsResult = Future.successful(AgentListCounts(0, 0)))
      val app                            = application(connector, sessionRepository)

      running(app) {
        val content = contentAsString(route(app, FakeRequest(GET, authorisationsRoute)).value)

        content must include("Unsubmitted requests (0)")
        content must include("Waiting for an authorisation code (0)")
      }
    }

    "must show the Government Gateway identifier as <agent ID>-<agent code>" in {

      val (connector, sessionRepository) = mocks()
      val app                            = application(connector, sessionRepository)

      running(app) {
        val content = contentAsString(route(app, FakeRequest(GET, authorisationsRoute)).value)

        content must include("Government Gateway identifier: 12345678-AGENTCODE1")
      }
    }

    "must not put the agent code in any link" in {

      val (connector, sessionRepository) = mocks()
      val app                            = application(connector, sessionRepository)

      running(app) {
        val content = contentAsString(route(app, FakeRequest(GET, authorisationsRoute)).value)

        """href="[^"]*AGENTCODE1""".r.findFirstIn(content) mustBe None
      }
    }

    "must point each link at its page" in {

      val (connector, sessionRepository) = mocks()
      val app                            = application(connector, sessionRepository, FakeIdentifierAction.agent.copy(isAssistant = false))

      running(app) {
        val content = contentAsString(route(app, FakeRequest(GET, authorisationsRoute)).value)

        def href(id: String): String =
          s"""id="$id" href="([^"]*)"""".r.findFirstMatchIn(content).map(_.group(1)).getOrElse(fail(s"no link $id"))

        val ac1 = "/online-agent-authorisations-frontend/agent-authorisation"

        href("request-authorisation") mustEqual s"$ac1/request-authorisation"
        href("unsubmitted-requests") mustEqual s"$ac1/unsubmitted-requests"
        href("pending-requests") mustEqual s"$ac1/pending-requests"
        href("change-details") mustEqual s"$ac1/your-account/change-details"
        href("change-password") mustEqual s"$ac1/your-account/change-password"
        href("manage-team") mustEqual "#"
        href("manage-team-access") mustEqual "#"
        href("remove-services") mustEqual "/home/removeservices"
        href("faqs") mustEqual "/information/faqs/yourAccount"
        href("vat-for-agents") mustEqual "#"
        href("give-feedback") must include("/contact/beta-feedback?service=online-agent-authorisations-frontend")
        href("legal-changes") mustEqual "#"
        href("your-hmrc-services") mustEqual "/home"
        href("tools-and-calculators") mustEqual "/home/toolsandcalcs"
        content must include("""href="https://www.gov.uk/guidance/sign-in-to-your-agent-services-account" target="_blank"""")
      }
    }

    "must show the admin-only links to an administrator" in {

      val (connector, sessionRepository) = mocks()
      val app                            = application(connector, sessionRepository, FakeIdentifierAction.agent.copy(isAssistant = false))

      running(app) {
        val content = contentAsString(route(app, FakeRequest(GET, authorisationsRoute)).value)

        content must include("Change your details")
        content must include("Change your password")
        content must include("""id="manage-team"""")
        content must include("""id="manage-team-access"""")
        content must include("""id="remove-services"""")
      }
    }

    "must only show change details and change password to an assistant" in {

      val (connector, sessionRepository) = mocks()
      val app                            = application(connector, sessionRepository, FakeIdentifierAction.agent.copy(isAssistant = true))

      running(app) {
        val content = contentAsString(route(app, FakeRequest(GET, authorisationsRoute)).value)

        content must include("Change your details")
        content must include("Change your password")
        content must not include """id="manage-team""""
        content must not include """id="manage-team-access""""
        content must not include """id="remove-services""""
      }
    }

    "must remove the journey sections from the session and keep any other data" in {

      val existing = UserAnswers(
        "id",
        Json.obj(
          "request"    -> Json.obj("service" -> "SA"),
          "authorise"  -> Json.obj("selectedRequestIds" -> Json.arr(42)),
          "delete"     -> Json.obj("selectedRequestIds" -> Json.arr(42)),
          "submission" -> Json.obj("requestIds" -> Json.arr(42)),
          "codeEntry"  -> Json.obj("requestId" -> 42, "result" -> "accepted"),
          "other"      -> Json.obj("keep" -> true)
        )
      )

      val (connector, sessionRepository) = mocks(userAnswers = Some(existing))
      val app                            = application(connector, sessionRepository)

      running(app) {
        status(route(app, FakeRequest(GET, authorisationsRoute)).value) mustEqual OK

        val captor = ArgumentCaptor.forClass(classOf[UserAnswers])
        verify(sessionRepository).get(meq("id"))
        verify(sessionRepository).set(captor.capture())

        captor.getValue.data mustEqual Json.obj("other" -> Json.obj("keep" -> true))
      }
    }

    "must not write to the session when there is no saved data" in {

      val (connector, sessionRepository) = mocks(userAnswers = None)
      val app                            = application(connector, sessionRepository)

      running(app) {
        status(route(app, FakeRequest(GET, authorisationsRoute)).value) mustEqual OK

        verify(sessionRepository, never()).set(any())
      }
    }

    "must fail, and so show the error page, when the backend returns an error" in {

      val (connector, sessionRepository) =
        mocks(countsResult = Future.failed(UpstreamErrorResponse("boom", INTERNAL_SERVER_ERROR)))
      val app = application(connector, sessionRepository)

      running(app) {
        val result = route(app, FakeRequest(GET, authorisationsRoute)).value

        result.failed.futureValue mustBe an[UpstreamErrorResponse]
        verify(sessionRepository, never()).set(any())
      }
    }

    "must give a 500 with 'Sorry, there is a problem with the service' for a backend error" in {

      val (connector, sessionRepository) = mocks()
      val app                            = application(connector, sessionRepository)

      running(app) {
        val errorHandler = app.injector.instanceOf[ErrorHandler]
        val result = errorHandler.onServerError(
          FakeRequest(GET, authorisationsRoute),
          UpstreamErrorResponse("boom", INTERNAL_SERVER_ERROR)
        )

        status(result) mustEqual INTERNAL_SERVER_ERROR
        contentAsString(result) must include("Sorry, there is a problem with the service")
      }
    }

    "must give a 500 with 'Sorry, there is a problem with the service' for a 503 or a timeout from the backend" in {

      val (connector, sessionRepository) = mocks()
      val app                            = application(connector, sessionRepository)

      running(app) {
        val errorHandler = app.injector.instanceOf[ErrorHandler]

        Seq(
          UpstreamErrorResponse("unavailable", SERVICE_UNAVAILABLE),
          new uk.gov.hmrc.http.GatewayTimeoutException("timed out")
        ).foreach { error =>
          val result = errorHandler.onServerError(FakeRequest(GET, authorisationsRoute), error)

          status(result) mustEqual INTERNAL_SERVER_ERROR
          contentAsString(result) must include("Sorry, there is a problem with the service")
        }
      }
    }

    "must not call the backend when the auth action rejects the user" in {

      val (connector, sessionRepository) = mocks()
      val app                            = applicationWith(connector, sessionRepository, new RejectingIdentifierAction)

      running(app) {
        val result = route(app, FakeRequest(GET, authorisationsRoute)).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.UnauthorisedController.onPageLoad().url
        verify(connector, never()).countAgentLists(any())(any())
      }
    }

    "must not accept a POST" in {

      val (connector, sessionRepository) = mocks()
      val app                            = application(connector, sessionRepository)

      running(app) {
        val result = route(app, FakeRequest(POST, authorisationsRoute))

        result.map(status(_)).getOrElse(NOT_FOUND) must (equal(NOT_FOUND) or equal(METHOD_NOT_ALLOWED))
      }
    }
  }
}
