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
import config.FrontendAppConfig
import controllers.actions.{FakeAuthConnector, FakeFailingAuthConnector}
import models.AgentEnrolment
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.*
import views.html.ActivateAgentEnrolmentView

class ActivateAgentEnrolmentControllerSpec extends SpecBase {

  private lazy val activateAgentEnrolmentRoute: String = routes.ActivateAgentEnrolmentController.onPageLoad().url

  private def enrolment(key: String, identifierName: String, state: String): Enrolment =
    Enrolment(key, Seq(EnrolmentIdentifier(identifierName, "REF0001")), state)

  private def applicationWith(authConnector: AuthConnector) =
    applicationBuilder(userAnswers = None)
      .overrides(bind[AuthConnector].toInstance(authConnector))
      .build()

  "ActivateAgentEnrolment Controller" - {

    "must return OK and the correct view for a GET when one enrolment is not yet activated" in {

      val enrolments  = Enrolments(Set(enrolment("IR-SA-AGENT", "IRAgentReference", "NotYetActivated")))
      val application = applicationWith(new FakeAuthConnector(enrolments))

      running(application) {
        val request = FakeRequest(GET, activateAgentEnrolmentRoute)

        val result = route(application, request).value

        val view = application.injector.instanceOf[ActivateAgentEnrolmentView]

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(Seq(AgentEnrolment.SaAgent))(request, messages(application)).toString
      }
    }

    "must return OK and list every not yet activated enrolment, ignoring activated ones" in {

      val enrolments = Enrolments(Set(
        enrolment("IR-SA-AGENT", "IRAgentReference", "Activated"),
        enrolment("IR-CT-AGENT", "IRAgentReference", "NotYetActivated"),
        enrolment("HMRC-MGD-AGNT", "HMRCMGDAGENTREF", "NotYetActivated")
      ))
      val application = applicationWith(new FakeAuthConnector(enrolments))

      running(application) {
        val request = FakeRequest(GET, activateAgentEnrolmentRoute)

        val result = route(application, request).value

        val view = application.injector.instanceOf[ActivateAgentEnrolmentView]

        status(result) mustEqual OK
        contentAsString(result) mustEqual
          view(Seq(AgentEnrolment.CtAgent, AgentEnrolment.MgdAgent))(request, messages(application)).toString
      }
    }

    "must redirect to the index page when the agent has nothing to activate" in {

      val enrolments  = Enrolments(Set(enrolment("IR-SA-AGENT", "IRAgentReference", "Activated")))
      val application = applicationWith(new FakeAuthConnector(enrolments))

      running(application) {
        val request = FakeRequest(GET, activateAgentEnrolmentRoute)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.IndexController.onPageLoad().url
      }
    }

    "must redirect to sign in when the user is not signed in" in {

      val application = applicationWith(new FakeFailingAuthConnector(new MissingBearerToken))

      running(application) {
        val request = FakeRequest(GET, activateAgentEnrolmentRoute)

        val result = route(application, request).value

        val appConfig = application.injector.instanceOf[FrontendAppConfig]

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value must startWith(appConfig.loginUrl)
      }
    }

    "must redirect to the unauthorised page when the user is not an agent" in {

      val application = applicationWith(new FakeFailingAuthConnector(new UnsupportedAffinityGroup))

      running(application) {
        val request = FakeRequest(GET, activateAgentEnrolmentRoute)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.UnauthorisedController.onPageLoad().url
      }
    }
  }
}
