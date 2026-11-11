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

package models.requests

import models.AgentService

/**
 * Everything the auth action learns about the signed-in agent.
 *
 * @param agentCode       Government Gateway agent code (AGENT_ID / AGENT_CODE / GATEWAY_AGENT_ID)
 * @param credentialId    GG credential id (USER_ID on tracking messages)
 * @param email           used to pre-fill the email address page
 * @param agentReferences agent reference per activated service (IR_AGENT_REF)
 */
final case class AgentDetails(
  agentCode: String,
  credentialId: String,
  email: Option[String],
  agentReferences: Map[AgentService, String]
):

  val activatedServices: Seq[AgentService] =
    AgentService.values.toSeq.filter(agentReferences.contains)

  def agentReference(service: AgentService): Option[String] =
    agentReferences.get(service)

trait AgentRequest:
  def agent: AgentDetails

  def agentCode: String                    = agent.agentCode
  def credentialId: String                 = agent.credentialId
  def email: Option[String]                = agent.email
  def activatedServices: Seq[AgentService] = agent.activatedServices
  def agentReference(service: AgentService): Option[String] = agent.agentReference(service)
