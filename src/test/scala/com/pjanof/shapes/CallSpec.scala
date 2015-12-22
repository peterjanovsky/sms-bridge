package com.pjanof.shapes

import org.scalatest._
import spray.json._

class CallSpec extends FlatSpec with Matchers with CallProtocol {

  "Call" should "marshal to/from JSON" in {

    val to = "+12345678910"
    val from = "+19876543210"

    val call = Call(to, from)

    val json: JsValue = call.toJson
    json should be ( s"""{"to":"$to","from":"$from"}""".parseJson )

    val converted: Call = json.convertTo[Call]
    converted.to should be (to)
    converted.from should be (from)
  }

  it should "fail with DeserializationException when field is missing" in {

    intercept[DeserializationException] {

      val json: JsValue = s"""{"to":"+12345678910"}""".parseJson
      json.convertTo[Call]
    }

    intercept[DeserializationException] {

      val json: JsValue = s"""{"from":"+19876543210"}""".parseJson
      json.convertTo[Call]
    }
  }

  it should "fail with IllegalArgumentException when field is null" in {

    intercept[IllegalArgumentException] {

      val call: Call = Call("+12345678910", null)
      call.toJson
    }
  }

  it should "fail with NullPointerException when Call is null" in {

    intercept[NullPointerException] {

      val call: Call = null
      call.toJson
    }
  }
}
