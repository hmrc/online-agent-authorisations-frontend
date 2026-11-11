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

import config.FrontendAppConfig
import models.AgentEnrolment
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.ActivateAgentEnrolmentView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ActivateAgentEnrolmentController @Inject()(
                                                  override val authConnector: AuthConnector,
                                                  config: FrontendAppConfig,
                                                  val controllerComponents: MessagesControllerComponents,
                                                  view: ActivateAgentEnrolmentView
                                                )(implicit ec: ExecutionContext)
  extends FrontendBaseController with I18nSupport with AuthorisedFunctions {

  def onPageLoad(): Action[AnyContent] = Action.async { implicit request =>
    authorised(AffinityGroup.Agent).retrieve(Retrievals.allEnrolments) { enrolments =>
      val notActivated = AgentEnrolment.notActivated(enrolments)
      if (notActivated.isEmpty) {
        Future.successful(Redirect(routes.IndexController.onPageLoad()))
      } else {
        Future.successful(Ok(view(notActivated)))
      }
    } recover {
      case _: NoActiveSession =>
        Redirect(config.loginUrl, Map("continue" -> Seq(s"${config.host}${request.uri}")))
      case _: AuthorisationException =>
        Redirect(routes.UnauthorisedController.onPageLoad())
    }
  }
}
