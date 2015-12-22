package com.pjanof.communications

import com.typesafe.config.Config
import com.typesafe.config.ConfigObject
import com.typesafe.scalalogging.StrictLogging

import gov.nist.javax.sip.message.SIPMessage
import gov.nist.javax.sip.message.SIPRequest
import gov.nist.javax.sip.message.SIPResponse
import gov.nist.javax.sip.stack.SIPDialog
import gov.nist.javax.sip.Utils

import java.util.concurrent.Executors
import java.util.Properties

import javax.sip.address.AddressFactory
import javax.sip.address.SipURI
import javax.sip.address.TelURL
import javax.sip.header.SubscriptionStateHeader
import javax.sip.header.EventHeader
import javax.sip.header.HeaderFactory
import javax.sip.message.MessageFactory
import javax.sip.ClientTransaction
import javax.sip.DialogTerminatedEvent
import javax.sip.IOExceptionEvent
import javax.sip.ListeningPoint
import javax.sip.RequestEvent
import javax.sip.ResponseEvent
import javax.sip.SipFactory
import javax.sip.SipListener
import javax.sip.SipProvider
import javax.sip.ServerTransaction
import javax.sip.TimeoutEvent
import javax.sip.TransactionTerminatedEvent

import org.slf4j.LoggerFactory
import org.mobicents.ext.javax.sip.SipStackImpl

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps

trait SipStack {

  val sipFactory: SipFactory = SipFactory.getInstance

  val addressFactory: AddressFactory = sipFactory.createAddressFactory

  val headerFactory: HeaderFactory = sipFactory.createHeaderFactory

  val messageFactory: MessageFactory = sipFactory.createMessageFactory

  val utils: Utils = Utils.getInstance
}

class Sip private(  uris: Vector[String]
                  , props: Properties
                  , requestHandler: RequestEvent => Unit
                  , responseHandler: ResponseEvent => Unit
                  , timeoutHandler: TimeoutEvent => Unit)(implicit ec: ExecutionContext)

  extends SipStack with SipListener with StrictLogging {

  val stackPath = "org.mobicents.ext"
  sipFactory setPathName (stackPath)

  private val stack: SipStackImpl = sipFactory.createSipStack(props) match {
    case impl: SipStackImpl => impl
  }

  private val listeningPoints = uris map { uri =>

    val sipURI: SipURI = addressFactory.createURI(uri) match { case sipURI: SipURI => sipURI }

    stack.createListeningPoint(
      sipURI.getHost,
      sipURI.getPort,
      sipURI.getTransportParam)

  } toIndexedSeq

  private val provider = stack.createSipProvider(listeningPoints.head)
  provider.addSipListener(this)

  listeningPoints.tail map (provider.addListeningPoint)

  override def processDialogTerminated(terminatedEvent: DialogTerminatedEvent): Unit =
    logger.error(s"Dialog Terminated: $terminatedEvent")

  override def processIOException(exceptionEvent: IOExceptionEvent): Unit =
    logger.error(s"IO Exception: $exceptionEvent")

  override def processRequest(requestEvent: RequestEvent): Unit =
    Future(requestHandler(requestEvent))

  override def processResponse(responseEvent: ResponseEvent): Unit =
    Future(responseHandler(responseEvent))

  override def processTimeout(timeoutEvent: TimeoutEvent): Unit =
    Future(timeoutHandler(timeoutEvent))

  override def processTransactionTerminated(terminatedEvent: TransactionTerminatedEvent): Unit =
    logger.error(s"Transaction Terminated: $terminatedEvent")

  def start: Unit = stack start

  def stop: Unit = stack stop

  def sendRequest(request: SIPRequest): Unit = provider.sendRequest(request)

  def sendResponse(response: SIPResponse): Unit = provider.sendResponse(response)

  def withClientTransaction[T](request: SIPRequest)(f: ClientTransaction => T): T =
    f(provider.getNewClientTransaction(request))
}

object Sip {

  def apply(  name: String
            , numOfThreads: Int
            , config: Config
            , requestHandler: RequestEvent => Unit
            , responseHandler: ResponseEvent => Unit
            , timeoutHandler: TimeoutEvent => Unit)(implicit ec: ExecutionContext): Sip = {

    val props: Properties = new Properties
    props.put("javax.sip.STACK_NAME", name)
    props.put("gov.nist.javax.sip.THREAD_POOL_SIZE", numOfThreads.toString)

    val configObj: ConfigObject = config.getObject("stack.properties")
    configObj.keySet.map { key => props.put(key, configObj.get(key).unwrapped.toString) }

    val uris: Vector[String] = config.getStringList("stack.uris").asScala.toVector

    new Sip(uris, props, requestHandler, responseHandler, timeoutHandler)
  }

  def withRequest[A](re: RequestEvent)(f: SIPRequest => A): A =

    re.getRequest match {

      case request: SIPRequest => f(request)

    }

  def withResponse[A](re: ResponseEvent)(f: SIPResponse => A): A =

    re.getResponse match {

      case response: SIPResponse => f(response)

    }
}
