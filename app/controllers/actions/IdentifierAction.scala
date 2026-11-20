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
import uk.gov.hmrc.auth.core.retrieve.{AgentInformation, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait IdentifierAction extends ActionBuilder[IdentifierRequest, AnyContent] with ActionFunction[Request, IdentifierRequest]

class AuthenticatedIdentifierAction @Inject()(
                                               override val authConnector: AuthConnector,
                                               config: FrontendAppConfig,
                                               val parser: BodyParsers.Default
                                             )(implicit val executionContext: ExecutionContext)
  extends IdentifierAction with AuthorisedFunctions with Logging {

  // agentInformation carries both the agent code and the agent ID
  private val retrievals =
    Retrievals.internalId and Retrievals.affinityGroup and Retrievals.agentInformation and
      Retrievals.credentials and Retrievals.email and Retrievals.allEnrolments and Retrievals.credentialRole

  private val unauthorised: Result = Redirect(routes.UnauthorisedController.onPageLoad())

  override def invokeBlock[A](request: Request[A], block: IdentifierRequest[A] => Future[Result]): Future[Result] = {

    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    // Auth failures are handled here; anything the page itself throws (such as a backend error) is left to the error handler
    val identified: Future[Either[Result, IdentifierRequest[A]]] =
      authorised().retrieve(retrievals) {
        case Some(internalId) ~ Some(AffinityGroup.Agent) ~ AgentInformation(agentId, Some(agentCode), _) ~ Some(credentials) ~
          email ~ enrolments ~ credentialRole if agentCode.nonEmpty =>
          val agentReferences = AgentEnrolment.agentReferences(enrolments)
          if (agentReferences.isEmpty) {
            if (AgentEnrolment.hasNotActivatedEnrolment(enrolments)) {
              logger.info("[AuthenticatedIdentifierAction] Agent has OAA enrolments but none are activated")
              //TODO check what the actual things the agents need to do to activate their enrolments
              Future.successful(Left(Redirect(routes.ActivateAgentEnrolmentController.onPageLoad())))
            } else {
              logger.warn("[AuthenticatedIdentifierAction] Agent has no OAA enrolments")
              Future.successful(Left(unauthorised))
            }
          } else {
            Future.successful(Right(IdentifierRequest(
              request,
              internalId,
              AgentDetails(
                agentCode       = agentCode,
                agentId         = agentId,
                credentialId    = credentials.providerId,
                email           = email,
                isAssistant     = credentialRole.contains(Assistant),
                agentReferences = agentReferences
              )
            )))
          }

        case None ~ _ ~ _ ~ _ ~ _ ~ _ ~ _ =>
          logger.warn("[AuthenticatedIdentifierAction] Unable to retrieve internal Id")
          Future.successful(Left(unauthorised))

        case _ ~ Some(AffinityGroup.Agent) ~ _ ~ _ ~ _ ~ _ ~ _ =>
          logger.warn("[AuthenticatedIdentifierAction] Agent has no agent code or credentials")
          Future.successful(Left(unauthorised))

        case _ =>
          Future.successful(Left(unauthorised))
      } recover {
        case _: NoActiveSession =>
          Left(Redirect(config.loginUrl, Map("continue" -> Seq(s"${config.host}${request.uri}"))))
        case _: AuthorisationException =>
          Left(unauthorised)
        case NonFatal(e) =>
          logger.error(s"[AuthenticatedIdentifierAction] Auth failed: ${e.getMessage}")
          Left(unauthorised)
      }

    identified.flatMap {
      case Left(result)            => Future.successful(result)
      case Right(identifiedRequest) => block(identifiedRequest)
    }
  }
}
