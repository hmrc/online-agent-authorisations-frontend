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

package config

import com.google.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.i18n.Lang
import play.api.mvc.RequestHeader

@Singleton
class FrontendAppConfig @Inject() (configuration: Configuration) {

  val host: String    = configuration.get[String]("host")
  val appName: String = configuration.get[String]("appName")

  private val contactHost = configuration.get[String]("contact-frontend.host")
  private val contactFormServiceIdentifier = "online-agent-authorisations-frontend"

  def feedbackUrl(implicit request: RequestHeader): String =
    s"$contactHost/contact/beta-feedback?service=$contactFormServiceIdentifier&backUrl=${host + request.uri}"

  val loginUrl: String         = configuration.get[String]("urls.login")
  val loginContinueUrl: String = configuration.get[String]("urls.loginContinue")
  val signOutUrl: String       = configuration.get[String]("urls.signOut")

  private def authorisationsUrl(key: String): String = configuration.get[String](s"urls.authorisations.$key")

  val manageTeamUrl: String                = authorisationsUrl("manageTeam")
  val manageTeamAccessUrl: String          = authorisationsUrl("manageTeamAccess")
  val removeServicesUrl: String            = authorisationsUrl("removeServices")
  val faqsUrl: String                      = authorisationsUrl("faqs")
  val vatForAgentsUrl: String              = authorisationsUrl("vatForAgents")
  val legalChangesUrl: String              = authorisationsUrl("legalChanges")
  val yourHmrcServicesUrl: String          = authorisationsUrl("yourHmrcServices")
  val toolsAndCalculatorsUrl: String       = authorisationsUrl("toolsAndCalculators")
  val agentServicesAccountGuideUrl: String = authorisationsUrl("agentServicesAccountGuide")

  private val exitSurveyBaseUrl: String = configuration.get[Service]("microservice.services.feedback-frontend").baseUrl
  val exitSurveyUrl: String             = s"$exitSurveyBaseUrl/feedback/online-agent-authorisations-frontend"

  val onlineAgentAuthorisationsUrl: String =
    configuration.get[Service]("microservice.services.online-agent-authorisations").baseUrl

  val languageTranslationEnabled: Boolean =
    configuration.get[Boolean]("features.welsh-translation")

  def languageMap: Map[String, Lang] = Map(
    "en" -> Lang("en"),
    "cy" -> Lang("cy")
  )

  val timeout: Int   = configuration.get[Int]("timeout-dialog.timeout")
  val countdown: Int = configuration.get[Int]("timeout-dialog.countdown")

  val cacheTtl: Long = configuration.get[Int]("mongodb.timeToLiveInSeconds")
}
