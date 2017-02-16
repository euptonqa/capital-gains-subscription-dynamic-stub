/*
 * Copyright 2017 HM Revenue & Customs
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

package controllers.stubs

import actions.NinoExceptionTriggersActions
import javax.inject.{Inject, Singleton}
import helpers.SapHelper
import models.{BusinessPartnerModel, RegisterModel}
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import repositories.BusinessPartnerRepository
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class RegistrationController @Inject()(repository: BusinessPartnerRepository,
                                       sAPHelper: SapHelper,
                                       ninoExceptionTriggersActions: NinoExceptionTriggersActions) extends BaseController {

  val registerBusinessPartner: String => Action[AnyContent] = {
    nino =>
      ninoExceptionTriggersActions.WithNinoExceptionTriggers(Nino(nino)).async {
        implicit request => {

          Logger.warn("Received a call from the back end to register")

          val body = request.body.asJson
          val registrationDetails = body.get.as[RegisterModel]

          Logger.warn("Opening a connection to mongo.")

          val businessPartner = repository().findLatestVersionBy(registrationDetails.nino)

          Logger.warn("Promise of business partners established.")

          def getReference(bp: List[BusinessPartnerModel]): Future[String] = {

            Logger.warn("Was passed a list of bp's that is empty? -- " + bp.isEmpty)

            if (bp.isEmpty) {
              Logger.warn("Created a new entry with sap")
              val sap = sAPHelper.generateSap()
              for {
                _ <- repository().addEntry(BusinessPartnerModel(registrationDetails.nino, sap))
              } yield sap

            } else {
              Logger.warn("Found an existing entry with sap " + bp.head.sap)
              Future.successful(bp.head.sap)
            }
          }

          for {
            bp <- businessPartner
            sap <- getReference(bp)
          } yield Ok(Json.toJson(sap))
        }
      }
  }
}
