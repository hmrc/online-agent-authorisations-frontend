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

import uk.gov.hmrc.auth.core.Enrolments

enum AgentService(val code: String):
  case SA     extends AgentService("SA")
  case CT     extends AgentService("CT")
  case PAYE   extends AgentService("PAYE")
  case CIS    extends AgentService("CIS")
  case ERS    extends AgentService("PAYERS")
  case UKVAT  extends AgentService("UKVAT")
  case EUVAT  extends AgentService("EUVAT")
  case NOVA   extends AgentService("NOVA")
  case MOSS   extends AgentService("MOSS")
  case MOSSNU extends AgentService("MOSSNU")
  case MGD    extends AgentService("MGD")

object AgentService:

  def fromCode(code: String): Option[AgentService] =
    AgentService.values.find(_.code == code)

  def requestable: Seq[AgentService] =
    AgentService.values.toSeq.filterNot(_ == AgentService.UKVAT)

enum AgentEnrolment(val key: String, val identifier: String, val services: Seq[AgentService]):
  case SaAgent      extends AgentEnrolment("IR-SA-AGENT",     "IRAgentReference", Seq(AgentService.SA))
  case CtAgent      extends AgentEnrolment("IR-CT-AGENT",     "IRAgentReference", Seq(AgentService.CT))
  case PayeAgent    extends AgentEnrolment("IR-PAYE-AGENT",   "IRAgentReference", Seq(AgentService.PAYE, AgentService.CIS, AgentService.ERS))
  case HmceVatAgent extends AgentEnrolment("HMCE-VAT-AGNT",   "AgentRefNo",       AgentEnrolment.vatServices)
  case NovrnAgent   extends AgentEnrolment("HMRC-NOVRN-AGNT", "VATAgentRefNo",    AgentEnrolment.vatServices)
  case MgdAgent     extends AgentEnrolment("HMRC-MGD-AGNT",   "HMRCMGDAGENTREF",  Seq(AgentService.MGD))

object AgentEnrolment:
  private[models] def vatServices: Seq[AgentService] =
    Seq(AgentService.UKVAT, AgentService.EUVAT, AgentService.NOVA, AgentService.MOSS, AgentService.MOSSNU)

  def notActivated(enrolments: Enrolments): Seq[AgentEnrolment] =
    AgentEnrolment.values.toSeq.filter(e => enrolments.getEnrolment(e.key).exists(!_.isActivated))

  def hasNotActivatedEnrolment(enrolments: Enrolments): Boolean =
    notActivated(enrolments).nonEmpty

  def agentReferences(enrolments: Enrolments): Map[AgentService, String] =
    AgentEnrolment.values.foldLeft(Map.empty[AgentService, String]) { (acc, e) =>
      enrolments.getEnrolment(e.key)
        .filter(_.isActivated)
        .flatMap(_.getIdentifier(e.identifier))
        .map(_.value) match
          case Some(ref) => acc ++ e.services.filterNot(acc.contains).map(_ -> ref)
          case None      => acc
    }
