package com.pjanof

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
 
import com.pjanof.services.BridgeService
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging

import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.language.postfixOps

object Boot extends App with BridgeService with StrictLogging {

  override implicit val config: Config = ConfigFactory.load

  override implicit val system = ActorSystem("sms-bridge", config)

  override implicit val mat = ActorMaterializer()

  val processors: Int = Runtime.getRuntime.availableProcessors

  val es: ExecutorService = Executors.newFixedThreadPool(processors)

  override implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(es)

  val source: Source[Http.IncomingConnection, Future[Http.ServerBinding]] =
    Http().bind(config.getString("http.interface"), config.getInt("http.port"))

  val bindingFuture: Future[Http.ServerBinding] = source.to { Sink.foreach { connection =>

    logger.info(s"Accepted Connection ${connection.remoteAddress}")

    /** equivalent of
      * connection handleWith { Flow[HttpRequest] mapAsync(1)(requestHandler) }
      */
    connection handleWithAsyncHandler requestHandler

  } } run
}
