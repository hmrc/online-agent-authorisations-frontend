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

import models.AgentService.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier, Enrolments}

class AgentEnrolmentSpec extends AnyFreeSpec with Matchers {

  private def enrolment(key: String, identifierName: String, value: String, state: String = "Activated"): Enrolment =
    Enrolment(key, Seq(EnrolmentIdentifier(identifierName, value)), state)

  "AgentEnrolment" - {

    "must give every enrolment its services" in {
      AgentEnrolment.values.foreach(e => e.services must not be empty)
    }

    "agentReferences" - {

      "must map IR-PAYE-AGENT to PAYE, CIS and ERS, with ERS using the legacy code PAYERS" in {
        AgentEnrolment.agentReferences(Enrolments(Set(enrolment("IR-PAYE-AGENT", "IRAgentReference", "P1")))) mustBe
          Map(PAYE -> "P1", CIS -> "P1", ERS -> "P1")

        ERS.code mustBe "PAYERS"
      }

      "must map an activated HMCE-VAT-AGNT to UKVAT, EUVAT, NOVA, MOSS and MOSSNU" in {
        AgentEnrolment.agentReferences(Enrolments(Set(enrolment("HMCE-VAT-AGNT", "AgentRefNo", "V123")))) mustBe
          Map(UKVAT -> "V123", EUVAT -> "V123", NOVA -> "V123", MOSS -> "V123", MOSSNU -> "V123")
      }

      "must use HMRC-NOVRN-AGNT for all five VAT services when HMCE-VAT-AGNT is not yet activated" in {
        val enrolments = Enrolments(Set(
          enrolment("HMCE-VAT-AGNT", "AgentRefNo", "V123", "NotYetActivated"),
          enrolment("HMRC-NOVRN-AGNT", "VATAgentRefNo", "N456")
        ))

        AgentEnrolment.agentReferences(enrolments) mustBe
          Map(UKVAT -> "N456", EUVAT -> "N456", NOVA -> "N456", MOSS -> "N456", MOSSNU -> "N456")
      }

      "must prefer HMCE-VAT-AGNT over HMRC-NOVRN-AGNT when both are activated" in {
        val enrolments = Enrolments(Set(
          enrolment("HMRC-NOVRN-AGNT", "VATAgentRefNo", "NOVRN1"),
          enrolment("HMCE-VAT-AGNT", "AgentRefNo", "HMCE1")
        ))

        AgentEnrolment.agentReferences(enrolments) mustBe
          Map(UKVAT -> "HMCE1", EUVAT -> "HMCE1", NOVA -> "HMCE1", MOSS -> "HMCE1", MOSSNU -> "HMCE1")
      }

      "must ignore enrolments that are not activated" in {
        AgentEnrolment.agentReferences(Enrolments(Set(enrolment("IR-SA-AGENT", "IRAgentReference", "SA1", "NotYetActivated")))) mustBe
          Map.empty
      }

      "must ignore enrolments with the wrong identifier name" in {
        AgentEnrolment.agentReferences(Enrolments(Set(enrolment("IR-SA-AGENT", "UTR", "SA1")))) mustBe Map.empty
      }
    }

    "notActivated" - {

      "must return the OAA enrolments that are not activated, in a stable order" in {
        val enrolments = Enrolments(Set(
          enrolment("HMRC-MGD-AGNT", "HMRCMGDAGENTREF", "M1", "NotYetActivated"),
          enrolment("IR-SA-AGENT", "IRAgentReference", "S1", "NotYetActivated"),
          enrolment("IR-CT-AGENT", "IRAgentReference", "C1"),
          enrolment("IR-SA", "UTR", "U1", "NotYetActivated")
        ))

        AgentEnrolment.notActivated(enrolments) mustBe Seq(AgentEnrolment.SaAgent, AgentEnrolment.MgdAgent)
        AgentEnrolment.hasNotActivatedEnrolment(enrolments) mustBe true
      }

      "must be empty when every OAA enrolment is activated" in {
        val enrolments = Enrolments(Set(enrolment("IR-SA-AGENT", "IRAgentReference", "S1")))

        AgentEnrolment.notActivated(enrolments) mustBe empty
        AgentEnrolment.hasNotActivatedEnrolment(enrolments) mustBe false
      }
    }
  }

  "AgentService" - {

    "must use the legacy service codes" in {
      AgentService.values.map(s => s -> s.code).toMap mustBe Map(
        SA     -> "SA",
        CT     -> "CT",
        PAYE   -> "PAYE",
        CIS    -> "CIS",
        ERS    -> "PAYERS",
        UKVAT  -> "UKVAT",
        EUVAT  -> "EUVAT",
        NOVA   -> "NOVA",
        MOSS   -> "MOSS",
        MOSSNU -> "MOSSNU",
        MGD    -> "MGD"
      )
    }

    "fromCode" - {

      "must find a service by its legacy code" in {
        AgentService.fromCode("PAYERS") mustBe Some(ERS)
        AgentService.fromCode("UKVAT") mustBe Some(UKVAT)
      }

      "must round trip every service's code" in {
        AgentService.values.foreach(s => AgentService.fromCode(s.code) mustBe Some(s))
      }

      "must not match the enum name when it differs from the code" in {
        AgentService.fromCode("ERS") mustBe None
      }

      "must match case-sensitively" in {
        AgentService.fromCode("payers") mustBe None
      }
    }

    "requestable" - {

      "must not include UKVAT" in {
        AgentService.requestable must not contain UKVAT
      }

      "must include every other service" in {
        AgentService.requestable mustBe AgentService.values.toSeq.filterNot(_ == UKVAT)
      }
    }
  }
}
