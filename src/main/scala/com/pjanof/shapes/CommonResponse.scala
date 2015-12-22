package com.pjanof.shapes

import spray.json._

object ResponseStatuses {

  sealed trait ResponseStatus
  case object Error extends ResponseStatus
  case object OK extends ResponseStatus

  object ResponseStatusJsonFormat extends RootJsonFormat[ResponseStatus] {

    def write(status: ResponseStatus) = JsString(status.toString.toLowerCase)

    def read(value: JsValue): ResponseStatus = value match {
      case JsString("error") => Error
      case JsString("ok") => OK
      case e => deserializationError(s"Expected ResponseStatus as JsString, but got $e")
    }
  }
}

sealed trait CommonResponse

case class ErrorResponse(message: String) extends CommonResponse {

  val status: ResponseStatuses.ResponseStatus = ResponseStatuses.Error
}

case class ValidResponse[A](payload: A) extends CommonResponse {

  val status: ResponseStatuses.ResponseStatus = ResponseStatuses.OK
}

trait CommonResponseProtocol extends DefaultJsonProtocol {

  import ResponseStatuses.ResponseStatusJsonFormat

  implicit def errorResponseFormat = new RootJsonFormat[ErrorResponse] {

    def write(response: ErrorResponse): JsObject = JsObject(
      "status" -> ResponseStatusJsonFormat.write(response.status),
      "message" -> {
        if ((response.message ne null) && (!response.message.isEmpty)) JsString(response.message)
        else throw new SerializationException("Response message must not be empty") } )

    def read(value: JsValue): ErrorResponse =
      value.asJsObject.getFields("status", "message") match {
        case Seq(JsString(status), JsString(message)) => ErrorResponse(message)
        case e => deserializationError(s"Expected ErrorReponse as JsObject, but got $e")
      }
  }

  implicit def validResponseFormat[A :JsonFormat] = new RootJsonFormat[ValidResponse[A]] {

    def write(response: ValidResponse[A]) = JsObject(
      "status" -> ResponseStatusJsonFormat.write(response.status),
      "payload" -> {
        if (response.payload != null) response.payload.toJson
        else throw new SerializationException("Response payload must not be null") } )

    def read(value: JsValue) =
      value.asJsObject.getFields("status", "payload") match {
        case Seq(JsString(status), payload: JsObject) => ValidResponse(payload.convertTo[A])
        case e => deserializationError(s"Expected ValidResponse as JsObject, but got $e")
    }
  }
}
