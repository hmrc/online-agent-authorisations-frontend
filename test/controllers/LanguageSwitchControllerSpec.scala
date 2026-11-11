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
import play.api.test.FakeRequest
import play.api.test.Helpers.*

class LanguageSwitchControllerSpec extends SpecBase {

  private val languageCookieName = "PLAY_LANG"

  private def switchLanguageRoute(language: String): String =
    routes.LanguageSwitchController.switchToLanguage(language).url

  "LanguageSwitch Controller" - {

    "must set the language to Welsh and redirect back to the referring page" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, switchLanguageRoute("cy"))
          .withHeaders(REFERER -> "/online-agent-authorisations-frontend/some-page")

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual "/online-agent-authorisations-frontend/some-page"
        cookies(result).get(languageCookieName).value.value mustEqual "cy"
      }
    }

    "must set the language to English and redirect back to the referring page" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, switchLanguageRoute("en"))
          .withHeaders(REFERER -> "/online-agent-authorisations-frontend/some-page")

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual "/online-agent-authorisations-frontend/some-page"
        cookies(result).get(languageCookieName).value.value mustEqual "en"
      }
    }

    "must redirect to the index page when there is no referring page" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, switchLanguageRoute("cy"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.IndexController.onPageLoad().url
        cookies(result).get(languageCookieName).value.value mustEqual "cy"
      }
    }

    "must keep the current language when an unsupported language is requested" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, switchLanguageRoute("fr"))
          .withHeaders(REFERER -> "/online-agent-authorisations-frontend/some-page")

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual "/online-agent-authorisations-frontend/some-page"
        cookies(result).get(languageCookieName).value.value mustEqual "en"
      }
    }
  }
}
