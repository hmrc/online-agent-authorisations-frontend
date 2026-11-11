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

import com.google.inject.Inject
import config.FrontendAppConfig
import controllers.routes
import models.AgentEnrolment
import models.requests.{AgentDetails, IdentifierRequest}
import play.api.Logging
import play.api.mvc.*
import play.api.mvc.Results.*
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

trait IdentifierAction extends ActionBuilder[IdentifierRequest, AnyContent] with ActionFunction[Request, IdentifierRequest]

class AuthenticatedIdentifierAction @Inject()(
                                               override val authConnector: AuthConnector,
                                               config: FrontendAppConfig,
                                               val parser: BodyParsers.Default
                                             )(implicit val executionContext: ExecutionContext)
  extends IdentifierAction with AuthorisedFunctions with Logging {

  private val retrievals =
    Retrievals.internalId and Retrievals.affinityGroup and Retrievals.agentCode and
      Retrievals.credentials and Retrievals.email and Retrievals.allEnrolments

  override def invokeBlock[A](request: Request[A], block: IdentifierRequest[A] => Future[Result]): Future[Result] = {

    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    authorised().retrieve(retrievals) {
      case None ~ _ ~ _ ~ _ ~ _ ~ _ =>
        Future.failed(new UnauthorizedException("Unable to retrieve internal Id"))

      case Some(internalId) ~ Some(AffinityGroup.Agent) ~ Some(agentCode) ~ Some(credentials) ~ email ~ enrolments
        if agentCode.nonEmpty =>
        val agentReferences = AgentEnrolment.agentReferences(enrolments)
        if (agentReferences.isEmpty) {
          if (AgentEnrolment.hasNotActivatedEnrolment(enrolments)) {
            logger.info("[AuthenticatedIdentifierAction] Agent has OAA enrolments but none are activated")
            //TODO check what the actual things the agents need to do to activate their enrolments
            Future.successful(Redirect(routes.ActivateAgentEnrolmentController.onPageLoad()))
          } else {
            logger.warn("[AuthenticatedIdentifierAction] Agent has no OAA enrolments")
            Future.successful(Redirect(routes.UnauthorisedController.onPageLoad()))
          }
        } else {
          block(IdentifierRequest(
            request,
            internalId,
            AgentDetails(agentCode, credentials.providerId, email, agentReferences)
          ))
        }

      case _ ~ Some(AffinityGroup.Agent) ~ _ ~ _ ~ _ ~ _ =>
        logger.warn("[AuthenticatedIdentifierAction] Agent has no agent code or credentials")
        Future.successful(Redirect(routes.UnauthorisedController.onPageLoad()))

      case _ =>
        Future.successful(Redirect(routes.UnauthorisedController.onPageLoad()))
    } recover {
      case _: NoActiveSession =>
        Redirect(config.loginUrl, Map("continue" -> Seq(s"${config.host}${request.uri}")))
      case _: AuthorisationException =>
        Redirect(routes.UnauthorisedController.onPageLoad())
    }
  }
}
