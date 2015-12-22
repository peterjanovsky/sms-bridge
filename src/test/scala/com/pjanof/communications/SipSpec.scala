package com.pjanof.communications

import com.typesafe.config.{ Config, ConfigFactory }
import com.typesafe.scalalogging.StrictLogging

import gov.nist.javax.sip.message.SIPRequest
import gov.nist.javax.sip.message.SIPResponse

import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import javax.sip.address.Address
import javax.sip.header.CallIdHeader
import javax.sip.header.ContentTypeHeader
import javax.sip.header.CSeqHeader
import javax.sip.header.FromHeader
import javax.sip.header.MaxForwardsHeader
import javax.sip.header.RouteHeader
import javax.sip.header.ToHeader
import javax.sip.header.ViaHeader
import javax.sip.message.Request.{ INFO, INVITE, OPTIONS }
import javax.sip.message.Response.{ OK, TRYING }
import javax.sip.RequestEvent
import javax.sip.ResponseEvent
import javax.sip.TimeoutEvent

import org.scalatest._

import scala.collection.JavaConversions._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.FiniteDuration

class SipSpec extends FlatSpec with Matchers with BeforeAndAfterAll
  with StrictLogging {

  val processors: Int = Runtime.getRuntime.availableProcessors
  val es: ExecutorService = Executors.newFixedThreadPool(processors)
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(es)

  val config1: Config = ConfigFactory.load("stack-1")

  val sip1 = Sip("test-stack-1", processors, config1.getConfig("sip"), requestHandler
    , responses.put, timeouts.put)

  def requestHandler(re: RequestEvent): Unit = re.getRequest match {

    case req: SIPRequest => req.getMethod match {

      // health check
      case INFO | OPTIONS =>

        val resp: SIPResponse = req.createResponse(OK)
        sip1.sendResponse(resp)

      case INVITE =>

        val resp: SIPResponse = req.createResponse(TRYING)
        sip1.sendResponse(resp)

      } }

  def responseHandler(re: ResponseEvent): Unit = re.getResponse match {

    case resp: SIPResponse => logger.info(s"SIPResponse: $resp") }

  def timeoutHandler(te: TimeoutEvent): Unit = logger.error(s"TimeoutEvent: $te")

  val config2: Config = ConfigFactory.load("stack-2")

  val requests = new LinkedBlockingQueue[RequestEvent]()
  val responses = new LinkedBlockingQueue[ResponseEvent]()
  val timeouts = new LinkedBlockingQueue[TimeoutEvent]()

  val sip2 = Sip("test-stack-2", processors, config2.getConfig("sip"), requestHandler
    , responseHandler, timeoutHandler)

  val timeout = FiniteDuration(5, "seconds")

  override def beforeAll() {
    logger.info("Starting SIP Stacks")
    sip1.start
    sip2.start
  }

  override def afterAll() {
    logger.info("Stopping SIP Stacks")
    sip1.stop
    sip2.stop
  }

  "SIP stack" should "send INVITE to another stack and receive TRYING" in {

    val tag: Long = 1123581321

    // FromHeader
    val userFrom: String = "+12345678910"
    val ipFrom: String = "127.0.0.1"
    val portFrom: Int = 5060
    val protocolFrom: String = "udp"

    val from: String = s"sip:$userFrom@$ipFrom:$portFrom;transport=$protocolFrom"
    val fromAddr: Address = sip1.addressFactory.createAddress(from)
    val fromHdr: FromHeader = sip1.headerFactory.createFromHeader(fromAddr, tag.toString)

    // ToHeader
    val userTo: String = "+19876543210"
    val ipTo: String = "127.0.0.1"
    val portTo: Int = 5061
    val protocolTo: String = "udp"

    val to: String = s"sip:$userTo@$ipTo:$portTo;transport=$protocolTo"
    val toAddr: Address = sip1.addressFactory.createAddress(sip1.addressFactory.createURI(to))
    val toHdr: ToHeader = sip1.headerFactory.createToHeader(toAddr, null)

    // CallHeader
    val callId: String = sip1.utils.generateCallIdentifier(from)
    val callIdHdr: CallIdHeader = sip1.headerFactory.createCallIdHeader(callId)

    // CSeqHeader
    val seqNum: Int = 1
    val method: String = "INVITE"

    val cSeqHdr: CSeqHeader = sip1.headerFactory.createCSeqHeader(seqNum, method)

    // MaxHeader
    val maxHdr: MaxForwardsHeader = sip1.headerFactory.createMaxForwardsHeader(70)

    // ViaHeader
    val viaHdr: ViaHeader = sip1.headerFactory.createViaHeader(ipFrom, portFrom, protocolFrom, null)

    val req: SIPRequest = sip1.messageFactory.createRequest(
      sip1.addressFactory.createURI(to),
      method,
      callIdHdr,
      cSeqHdr,
      fromHdr,
      toHdr,
      IndexedSeq( viaHdr ),
      maxHdr) match {

        case sipReq: SIPRequest =>

          // ContactHeader
          sipReq.setHeader(
            sip1.headerFactory.createContactHeader(
              sip1.addressFactory.createAddress(
                {
                  val sipURI = sip1.addressFactory.createSipURI(userFrom, ipFrom)
                  sipURI.setPort(portFrom)
                  sipURI.setTransportParam(protocolFrom)
                  sipURI
                })))

          // ContentTypeHeader
          val cTypeHdr: ContentTypeHeader = sip1.headerFactory.createContentTypeHeader("application", "sdp")
          sipReq.addHeader(cTypeHdr)

          sipReq
      }

    logger.info(s"SIP Message:\n$req")

    val trns = sip1.withClientTransaction(req) { tx =>
      tx.sendRequest
      tx
    }

    responses.poll(timeout.length, timeout.unit) match {
      case re: ResponseEvent =>
        re.getResponse match {
          case resp: SIPResponse => assert(resp.getStatusCode === TRYING)
          case unhandled => fail(s"Expected SIPResponse, Received: $unhandled")
        }
      case unhandled => fail(s"Expected ResponseEvent, Received: $unhandled")
    }
  }
}
