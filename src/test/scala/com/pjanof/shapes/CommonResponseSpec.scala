package com.pjanof.shapes

import org.scalatest._
import spray.json._

class CommonResponseSpec extends FlatSpec with Matchers with CommonResponseProtocol {

  "ResponseStatus" should "marshal to/from JSON" in {

    val errorJson = ResponseStatuses.ResponseStatusJsonFormat.write(ResponseStatuses.Error)
    errorJson should be ( JsString("error") )

    ResponseStatuses.ResponseStatusJsonFormat.read(errorJson) should be (ResponseStatuses.Error)

    val okJson = ResponseStatuses.ResponseStatusJsonFormat.write(ResponseStatuses.OK)
    okJson should be ( JsString("ok") )

    ResponseStatuses.ResponseStatusJsonFormat.read(okJson) should be (ResponseStatuses.OK)
  }

  "ErrorResponse" should "marshal to/from JSON" in {

    val message = "error-message"
    val response = ErrorResponse(message)

    val json: JsValue = response.toJson
    json should be ( s"""{"status":"error","message":"$message"}""".parseJson )

    val converted: ErrorResponse = json.convertTo[ErrorResponse]
    converted.status should be (ResponseStatuses.Error)
    converted.message should be (message)
  }

  it should "fail with DeserializationException when field message is missing" in {
    intercept[DeserializationException] {
      val json: JsValue = s"""{"status":"error"}""".parseJson 
      json.convertTo[ErrorResponse]
    }
  }

  it should "fail with SerializationException when the response does not contain a message" in {
    intercept[SerializationException] {
      val response = ErrorResponse("")
      response.toJson
    }
  }

  it should "fail with SerializationException when teh response's message is null" in {
    intercept[SerializationException] {
      val response = ErrorResponse(null)
      response.toJson
    }
  }

  "ValidResponse" should "marshal to/from JSON" in {

    case class Test1(a1: String, b1: Int)
    case class Test2(a2: String, b2: Test1)

    implicit val test1Format = jsonFormat2(Test1.apply)
    implicit val test2Format = jsonFormat2(Test2.apply)

    val test1 = Test1("foo", 1)
    val response1 = ValidResponse(test1)

    val json1: JsValue = response1.toJson
    json1 should be ( """{"status":"ok","payload":{"a1":"foo","b1":1}}""".parseJson )

    val converted1: ValidResponse[Test1] = json1.convertTo[ValidResponse[Test1]]
    converted1.status should be (ResponseStatuses.OK)
    converted1.payload should be (test1)

    val test2 = Test2("bar", test1)
    val response2 = ValidResponse(test2)

    val json2: JsValue = response2.toJson
    json2 should be ( """{"status":"ok","payload":{"a2":"bar","b2":{"a1":"foo","b1":1}}}""".parseJson )

    val converted2: ValidResponse[Test2] = json2.convertTo[ValidResponse[Test2]]
    converted2.status should be (ResponseStatuses.OK)
    converted2.payload should be (test2)
  }

  it should "fail with DeserializationException when field payload is missing" in {
    intercept[DeserializationException] {

      case class Test1(a: String)

      implicit val test1Format = jsonFormat1(Test1.apply)

      val json: JsValue = s"""{"status":"ok"}""".parseJson 
      json.convertTo[ValidResponse[Test1]]
    }
  }

  it should "fail with SerializationException when the response does not contain a message" in {
    intercept[SerializationException] {

      case class Test1(a1: String, b1: Int)

      implicit val test1Format = jsonFormat2(Test1.apply)

      val test1: Test1 = null
      val response = ValidResponse(test1)

      response.toJson
    }
  }
}
