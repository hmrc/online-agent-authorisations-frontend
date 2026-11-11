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

package models

import base.SpecBase
import pages.QuestionPage
import play.api.libs.json.*

import java.time.Instant
import scala.util.Try

class UserAnswersSpec extends SpecBase {

  private case object NamePage extends QuestionPage[String] {
    override def path: JsPath = JsPath \ "name"
  }

  private case object NestedPage extends QuestionPage[String] {
    override def path: JsPath = JsPath \ "section" \ "nested"
  }

  private case object AgePage extends QuestionPage[Int] {
    override def path: JsPath = JsPath \ "age"
  }

  // Clears NamePage whenever it is set or removed, to prove cleanup is called
  private case object ClearsNamePage extends QuestionPage[Boolean] {
    override def path: JsPath = JsPath \ "clearsName"

    override def cleanup(value: Option[Boolean], userAnswers: UserAnswers): Try[UserAnswers] =
      userAnswers.remove(NamePage)
  }

  // A path that can't be written when "name" already holds a string
  private case object UnderNamePage extends QuestionPage[String] {
    override def path: JsPath = JsPath \ "name" \ "child"
  }

  private val lastUpdated: Instant = Instant.ofEpochMilli(1700000000000L)

  "UserAnswers" - {

    "get" - {

      "must return the value when the page has an answer" in {
        val userAnswers = UserAnswers("id", Json.obj("name" -> "Alice"))

        userAnswers.get(NamePage).value mustEqual "Alice"
      }

      "must return a nested value" in {
        val userAnswers = UserAnswers("id", Json.obj("section" -> Json.obj("nested" -> "value")))

        userAnswers.get(NestedPage).value mustEqual "value"
      }

      "must return None when the page has no answer" in {
        emptyUserAnswers.get(NamePage) must not be defined
      }

      "must return None when the answer is the wrong type" in {
        val userAnswers = UserAnswers("id", Json.obj("age" -> "not a number"))

        userAnswers.get(AgePage) must not be defined
      }
    }

    "set" - {

      "must add the answer" in {
        val result = emptyUserAnswers.set(NamePage, "Alice").success.value

        result.get(NamePage).value mustEqual "Alice"
        result.data mustEqual Json.obj("name" -> "Alice")
      }

      "must replace an existing answer" in {
        val userAnswers = UserAnswers("id", Json.obj("name" -> "Alice"))

        userAnswers.set(NamePage, "Bob").success.value.get(NamePage).value mustEqual "Bob"
      }

      "must create the parent objects for a nested path" in {
        val result = emptyUserAnswers.set(NestedPage, "value").success.value

        result.data mustEqual Json.obj("section" -> Json.obj("nested" -> "value"))
      }

      "must keep the other answers" in {
        val userAnswers = UserAnswers("id", Json.obj("age" -> 30))

        val result = userAnswers.set(NamePage, "Alice").success.value

        result.get(AgePage).value mustEqual 30
        result.get(NamePage).value mustEqual "Alice"
      }

      "must run the page's cleanup" in {
        val userAnswers = UserAnswers("id", Json.obj("name" -> "Alice"))

        val result = userAnswers.set(ClearsNamePage, true).success.value

        result.get(ClearsNamePage).value mustEqual true
        result.get(NamePage) must not be defined
      }

      "must fail when the path can't be written" in {
        val userAnswers = UserAnswers("id", Json.obj("name" -> "Alice"))

        userAnswers.set(UnderNamePage, "child").failure.exception mustBe a[JsResultException]
      }
    }

    "remove" - {

      "must remove the answer" in {
        val userAnswers = UserAnswers("id", Json.obj("name" -> "Alice", "age" -> 30))

        val result = userAnswers.remove(NamePage).success.value

        result.get(NamePage) must not be defined
        result.data mustEqual Json.obj("age" -> 30)
      }

      "must leave the answers unchanged when the page has no answer" in {
        val userAnswers = UserAnswers("id", Json.obj("age" -> 30))

        userAnswers.remove(NamePage).success.value.data mustEqual Json.obj("age" -> 30)
      }

      "must run the page's cleanup" in {
        val userAnswers = UserAnswers("id", Json.obj("name" -> "Alice", "clearsName" -> true))

        val result = userAnswers.remove(ClearsNamePage).success.value

        result.data mustEqual Json.obj()
      }
    }

    "format" - {

      val userAnswers = UserAnswers("id", Json.obj("name" -> "Alice"), lastUpdated)

      val mongoJson = Json.obj(
        "_id"         -> "id",
        "data"        -> Json.obj("name" -> "Alice"),
        "lastUpdated" -> Json.obj("$date" -> Json.obj("$numberLong" -> lastUpdated.toEpochMilli.toString))
      )

      "must write to the Mongo document shape" in {
        Json.toJson(userAnswers) mustEqual mongoJson
      }

      "must read from the Mongo document shape" in {
        mongoJson.as[UserAnswers] mustEqual userAnswers
      }

      "must fail to read when a field is missing" in {
        (mongoJson - "_id").validate[UserAnswers] mustBe a[JsError]
      }
    }
  }
}
