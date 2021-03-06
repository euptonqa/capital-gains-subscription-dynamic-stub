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

import javax.inject.Singleton

import actions.ExceptionTriggersActions
import common.RouteIds
import helpers.SapHelper
import models._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.{CgtRepository, NonResidentBusinessPartnerRepository, RouteExceptionRepository, SchemaRepository}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import utils.SchemaValidation

import scala.concurrent.Future

@Singleton
class GhostRegistrationControllerSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  def setupController(findLatestVersionResult: List[NonResidentBusinessPartnerModel],
                      sap: String,
                      expectedExceptionCode: Option[Int] = None,
                      isValidJson: Boolean = true): GhostRegistrationController = {

    val mockCollection = mock[CgtRepository[NonResidentBusinessPartnerModel, FullDetailsModel]]
    val mockRepository = mock[NonResidentBusinessPartnerRepository]
    val mockSAPHelper = mock[SapHelper]
    val mockExceptionsCollection = mock[CgtRepository[RouteExceptionModel, RouteExceptionKeyModel]]
    val mockExceptionsRepository = mock[RouteExceptionRepository]
    val mockSchemaValidation = mock[SchemaValidation]
    val exceptionTriggersActions = new ExceptionTriggersActions(mockExceptionsRepository)
    val expectedException = expectedExceptionCode.fold(List[RouteExceptionModel]()) {
      code => List(RouteExceptionModel("", "", code))
    }

    when(mockExceptionsRepository.apply())
      .thenReturn(mockExceptionsCollection)

    when(mockExceptionsCollection.findLatestVersionBy(any())(any()))
      .thenReturn(Future.successful(expectedException))

    when(mockRepository.apply())
      .thenReturn(mockCollection)

    when(mockCollection.addEntry(any())(any()))
      .thenReturn(Future.successful({}))

    when(mockCollection.findLatestVersionBy(any())(any()))
      .thenReturn(Future.successful(findLatestVersionResult))

    when(mockSAPHelper.generateSap())
      .thenReturn(sap)

    when(mockSchemaValidation.validateJson(anyString(), any[JsValue]())).thenReturn(Future.successful(isValidJson))


    new GhostRegistrationController(mockRepository, mockSAPHelper, exceptionTriggersActions, mockSchemaValidation)
  }

  "Calling registerGhostBusinessPartner" when {

    "existing business partner exist" should {

      val fullDetailsModel = FullDetailsModel("Daniel", "Dorito", "25 Big House", "New York", None, None, None, "US")
      val controller = setupController(List(NonResidentBusinessPartnerModel(fullDetailsModel, "123456789")), "")
      lazy val result = controller.registerBusinessPartner()(FakeRequest("POST", "").withJsonBody(FullDetailsModel.asJson(fullDetailsModel)))

      "return a status of 200" in {
        status(result) shouldBe 200
      }
    }

    "no existing business partner exists" should {
      val fullDetailsModel = FullDetailsModel("Michael", "Dorito", "25 Big House", "New York", None, None, None, "US")
      val controller = setupController(Nil, "1234567890")
      lazy val result = controller.registerBusinessPartner()(FakeRequest("POST", "").withJsonBody(FullDetailsModel.asJson(fullDetailsModel)))

      "return a status of 200" in {
        status(result) shouldBe 200
      }

      "return a type of Json" in {
        contentType(result) shouldBe Some("application/json")
      }

      "return a valid SAP" in {
        val data = contentAsString(result)
        val json = Json.parse(data)
        (json \ "sapNumber").as[String] shouldBe "1234567890"
      }
    }
  }

  "an invalid JSON payload is sent" should {
    val fullDetailsModel = FullDetailsModel("Daniel", "Dorito", "25 Big House", "New York",
      None, None, None, "invalid country code that fails schema validation")
    val controller = setupController(List(NonResidentBusinessPartnerModel(fullDetailsModel, "123456789")), "", isValidJson = false)

    lazy val result = controller.registerBusinessPartner()(FakeRequest("POST", "").withJsonBody(FullDetailsModel.asJson(fullDetailsModel)))

    "return a status of 400" in {
      status(result) shouldBe 400
    }
  }
}
