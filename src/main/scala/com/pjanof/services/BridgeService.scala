package com.pjanof.services

import akka.actor.ActorSystem

import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.model.headers.`Content-Encoding`
import akka.http.scaladsl.model.headers.HttpEncodings

import HttpProtocols._
import MediaTypes._
import HttpMethods._

import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.unmarshalling.MultipartUnmarshallers
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.RunnableGraph
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Flow

import akka.util.ByteString

import com.pjanof.shapes.{ Call, CallProtocol, CommonResponseProtocol, ErrorResponse, ValidResponse }
import com.typesafe.config.{ Config,ConfigObject, ConfigRenderOptions }
import com.typesafe.scalalogging.StrictLogging

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

import spray.json._

import scalaz._
import Scalaz._

trait Protocols extends DefaultJsonProtocol with SprayJsonSupport
  with CallProtocol with CommonResponseProtocol

trait BridgeService extends Protocols with StrictLogging {

  implicit val config: Config

  implicit val system: ActorSystem
  implicit val mat: ActorMaterializer
  implicit val ec: ExecutionContext

  val requestHandler: HttpRequest => Future[HttpResponse] = {

    /** curl "http://127.0.0.1:9000/voice/call" \
      * -X POST \
      * -H "Content-Type: application/json"
      * -d '{"to":"+12345678910","from":"+19876543210"}'
      */
    case HttpRequest(POST, Uri(_, _, Uri.Path("/voice/call"), query: Uri.Query, _), _, entity: RequestEntity, _) =>

      logger.info(s"POST /voice/call with query string: $query")

      val callF: Future[Call] = Unmarshal(entity).to[Call]
      callF.flatMap { call =>

        val responseEntityF: Future[ResponseEntity] = Marshal(ValidResponse(call)).to[ResponseEntity]
        responseEntityF.map { responseEntity => HttpResponse(status = StatusCodes.OK, entity = responseEntity) }
      }

    // curl -X GET http://localhost:9000/config/sip
    case HttpRequest(GET, Uri(_, _, Path.Slash(Path.Segment("config", Path.Slash(Path.Segment(section: String, Path.Empty)))), _, _), _, _, _) =>

      val configObj: ConfigObject = config.getConfig(section).root

      val json: JsValue = configObj.render( ConfigRenderOptions.concise ).parseJson

      val responseEntityF: Future[ResponseEntity] = Marshal(ValidResponse(json)).to[ResponseEntity]
      responseEntityF.map { responseEntity => HttpResponse(status = StatusCodes.OK, entity = responseEntity) }

    case HttpRequest(GET, Uri.Path("/health"), _, _, _) =>

      Future { HttpResponse(entity = HttpEntity(MediaTypes.`application/json`, """{ "status": "ok" }""")) }

    case _: HttpRequest =>

      Future { HttpResponse(status = StatusCodes.NotFound, entity = "Unknown Resource") }
  }
}
