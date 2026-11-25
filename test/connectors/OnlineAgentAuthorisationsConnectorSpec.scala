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

package connectors

import com.github.tomakehurst.wiremock.client.WireMock.*
import models.AgentListCounts
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.*
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

class OnlineAgentAuthorisationsConnectorSpec
  extends AnyFreeSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with WireMockSupport {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val countAgentListsUrl = "/online-agent-authorisations/count-agent-lists"

  private lazy val app: Application =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.online-agent-authorisations.port" -> wireMockPort,
        "http-verbs.retries.enabled"                            -> false
      )
      .build()

  private def connector: OnlineAgentAuthorisationsConnector =
    app.injector.instanceOf[OnlineAgentAuthorisationsConnector]

  "OnlineAgentAuthorisationsConnector" - {

    "countAgentLists" - {

      "must send the agent code and return the counts" in {

        wireMockServer.stubFor(
          post(urlEqualTo(countAgentListsUrl))
            .withRequestBody(equalToJson("""{ "agentCode": "V3264H" }"""))
            .willReturn(okJson("""{ "unsubmitted": 2, "submitted": 1 }"""))
        )

        connector.countAgentLists("V3264H").futureValue mustBe AgentListCounts(unsubmitted = 2, submitted = 1)
      }

      "must return 0 and 0 when the backend returns 0 and 0" in {

        wireMockServer.stubFor(
          post(urlEqualTo(countAgentListsUrl))
            .willReturn(okJson("""{ "unsubmitted": 0, "submitted": 0 }"""))
        )

        connector.countAgentLists("V3264H").futureValue mustBe AgentListCounts(0, 0)
      }

      "must fail with an UpstreamErrorResponse when the backend returns a 5xx" in {

        wireMockServer.stubFor(
          post(urlEqualTo(countAgentListsUrl))
            .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
        )

        val error = connector.countAgentLists("V3264H").failed.futureValue

        error mustBe an[UpstreamErrorResponse]
        error.asInstanceOf[UpstreamErrorResponse].statusCode mustBe INTERNAL_SERVER_ERROR
      }

      "must fail when the backend does not respond in time" in {

        wireMockServer.stubFor(
          post(urlEqualTo(countAgentListsUrl))
            .willReturn(okJson("""{ "unsubmitted": 2, "submitted": 1 }""").withFixedDelay(5000))
        )

        val slowApp = new GuiceApplicationBuilder()
          .configure(
            "microservice.services.online-agent-authorisations.port" -> wireMockPort,
            "http-verbs.retries.enabled"                            -> false,
            "play.ws.timeout.request"                               -> "500ms"
          )
          .build()

        running(slowApp) {
          val slowConnector = slowApp.injector.instanceOf[OnlineAgentAuthorisationsConnector]

          slowConnector.countAgentLists("V3264H").failed.futureValue mustBe an[Exception]
        }
      }
    }
  }
}
