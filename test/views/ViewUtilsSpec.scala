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

package views

import base.SpecBase
import play.api.data.Form
import play.api.data.Forms.{single, text}
import play.api.i18n.Messages
import play.api.test.Helpers.*

class ViewUtilsSpec extends SpecBase {

  private val form: Form[String] = Form(single("value" -> text))

  private val formWithFieldError: Form[String]  = form.withError("value", "error.required")
  private val formWithGlobalError: Form[String] = form.withGlobalError("error.global")

  private def withMessages(test: Messages => Unit): Unit = {
    val application = applicationBuilder(userAnswers = None).build()

    running(application) {
      test(messages(application))
    }
  }

  "ViewUtils" - {

    "titleNoForm" - {

      "must build the title from the page title, service name and GOV.UK" in withMessages { implicit messages =>
        ViewUtils.titleNoForm("unauthorised.title") mustEqual
          s"${messages("unauthorised.title")} - ${messages("service.name")} - GOV.UK"
      }

      "must include the section between the page title and the service name when given" in withMessages { implicit messages =>
        ViewUtils.titleNoForm("unauthorised.title", section = Some("site.continue")) mustEqual
          s"${messages("unauthorised.title")} - ${messages("site.continue")} - ${messages("service.name")} - GOV.UK"
      }
    }

    "errorPrefix" - {

      "must be empty when the form has no errors" in withMessages { implicit messages =>
        ViewUtils.errorPrefix(form) mustEqual ""
      }

      "must be the error prefix when the form has a field error" in withMessages { implicit messages =>
        ViewUtils.errorPrefix(formWithFieldError) mustEqual messages("error.title.prefix")
      }

      "must be the error prefix when the form has a global error" in withMessages { implicit messages =>
        ViewUtils.errorPrefix(formWithGlobalError) mustEqual messages("error.title.prefix")
      }
    }

    "title" - {

      "must not start with the error prefix when the form has no errors" in withMessages { implicit messages =>
        val result = ViewUtils.title(form, "unauthorised.title")

        result must not startWith messages("error.title.prefix")
        result must endWith(s"${messages("unauthorised.title")} - ${messages("service.name")} - GOV.UK")
      }

      "must start with the error prefix when the form has errors" in withMessages { implicit messages =>
        ViewUtils.title(formWithFieldError, "unauthorised.title") mustEqual
          s"${messages("error.title.prefix")} ${messages("unauthorised.title")} - ${messages("service.name")} - GOV.UK"
      }

      "must include the section when given" in withMessages { implicit messages =>
        ViewUtils.title(formWithFieldError, "unauthorised.title", section = Some("site.continue")) mustEqual
          s"${messages("error.title.prefix")} ${messages("unauthorised.title")} - ${messages("site.continue")} - ${messages("service.name")} - GOV.UK"
      }
    }
  }
}
