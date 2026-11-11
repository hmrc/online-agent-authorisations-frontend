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

package viewmodels

import base.SpecBase
import play.api.i18n.Messages
import play.api.test.Helpers.*
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.Key

import scala.language.implicitConversions

class ImplicitConversionsSpec extends SpecBase {

  private def withMessages(test: Messages => Unit): Unit = {
    val application = applicationBuilder(userAnswers = None).build()

    running(application) {
      test(messages(application))
    }
  }

  "ImplicitConversions" - {

    "stringToText" - {

      "must turn a message key into Text with the translated message" in withMessages { implicit messages =>
        implicits.stringToText("site.continue") mustEqual Text(messages("site.continue"))
      }

      "must be applied automatically where Text is expected" in withMessages { implicit messages =>
        import implicits.*

        val text: Text = "site.continue"

        text mustEqual Text(messages("site.continue"))
      }

      "must use the key itself when there is no message for it" in withMessages { implicit messages =>
        implicits.stringToText("not.a.real.key") mustEqual Text("not.a.real.key")
      }
    }

    "stringToKey" - {

      "must turn a message key into a summary list Key with the translated message" in withMessages { implicit messages =>
        implicits.stringToKey("site.change") mustEqual Key(content = Text(messages("site.change")))
      }

      "must be applied automatically where a Key is expected" in withMessages { implicit messages =>
        import implicits.*

        val key: Key = "site.change"

        key mustEqual Key(content = Text(messages("site.change")))
      }

      "must use the key itself when there is no message for it" in withMessages { implicit messages =>
        implicits.stringToKey("not.a.real.key") mustEqual Key(content = Text("not.a.real.key"))
      }
    }
  }
}
