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

class TwilioSipSpec extends FlatSpec with Matchers with BeforeAndAfterAll
  with StrictLogging {

  val processors: Int = Runtime.getRuntime.availableProcessors
  val es: ExecutorService = Executors.newFixedThreadPool(processors)
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(es)

  val config: Config = ConfigFactory.load("twilio-stack")

  val sip = Sip("test-stack-1", processors, config.getConfig("sip"), requestHandler
    , responseHandler, timeoutHandler)

  def requestHandler(re: RequestEvent): Unit = re.getRequest match {

    case req: SIPRequest => req.getMethod match {

      // health check
      case INFO | OPTIONS =>

        val resp: SIPResponse = req.createResponse(OK)
        sip.sendResponse(resp)

      case INVITE =>

        val resp: SIPResponse = req.createResponse(TRYING)
        sip.sendResponse(resp)

      case unhandled => logger.error(s"Unhandled SIPRequest: $req") }

    case unhandled => logger.error(s"Expected SIPRequest, Received: $unhandled") }

  def responseHandler(re: ResponseEvent): Unit = re.getResponse match {

    case resp: SIPResponse => logger.info(s"SIPResponse: $resp")

    case unhandled => logger.error(s"Expected SIPResponse, Received: $unhandled") }

  def timeoutHandler(te: TimeoutEvent): Unit = logger.error(s"TimeoutEvent: $te")

  val requests = new LinkedBlockingQueue[RequestEvent]()
  val responses = new LinkedBlockingQueue[ResponseEvent]()
  val timeouts = new LinkedBlockingQueue[TimeoutEvent]()

  val timeout = FiniteDuration(5, "seconds")

  override def beforeAll() {
    logger.info("Starting SIP Stack")
    sip.start
  }

  override def afterAll() {
    logger.info("Stopping SIP Stack")
    sip.stop
  }

  "SIP" should "start stack" in {

    val tag: Long = 1123581321

    // FromHeader
    val userFrom: String = "+12345678910"
    val ipFrom: String = "127.0.0.1"
    val portFrom: Int = 5061
    val protocolFrom: String = "udp"

    val from: String = s"sip:$userFrom@$ipFrom:$portFrom;transport=$protocolFrom"
    val fromAddr: Address = sip.addressFactory.createAddress(from)
    val fromHdr: FromHeader = sip.headerFactory.createFromHeader(fromAddr, tag.toString)

    // ToHeader
    val userTo: String = "+19292444485"
    val ipTo: String = "pjanof.sip.twilio.com"
    val portTo: Int = 5060
    val protocolTo: String = "udp"

    val to: String = s"sip:$userTo@$ipTo:$portTo;transport=$protocolTo"
    val toAddr: Address = sip.addressFactory.createAddress(sip.addressFactory.createURI(to))
    val toHdr: ToHeader = sip.headerFactory.createToHeader(toAddr, null)

    // CallHeader
    val callId: String = sip.utils.generateCallIdentifier(from)
    val callIdHdr: CallIdHeader = sip.headerFactory.createCallIdHeader(callId)

    // CSeqHeader
    val seqNum: Int = 1
    val method: String = "INVITE"

    val cSeqHdr: CSeqHeader = sip.headerFactory.createCSeqHeader(seqNum, method)

    // MaxHeader
    val maxHdr: MaxForwardsHeader = sip.headerFactory.createMaxForwardsHeader(70)

    // ViaHeader
    val viaHdr: ViaHeader = sip.headerFactory.createViaHeader(ipFrom, portFrom, protocolFrom, null)

    val req: SIPRequest = sip.messageFactory.createRequest(
      sip.addressFactory.createURI(to),
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
            sip.headerFactory.createContactHeader(
              sip.addressFactory.createAddress(
                {
                  val sipURI = sip.addressFactory.createSipURI(userFrom, ipFrom)
                  sipURI.setPort(portFrom)
                  sipURI.setTransportParam(protocolFrom)
                  sipURI
                })))

          // ContentTypeHeader
          val cTypeHdr: ContentTypeHeader = sip.headerFactory.createContentTypeHeader("application", "sdp")
          sipReq.addHeader(cTypeHdr)

          sipReq
      }

    logger.info(s"SIP Message:\n$req")

    val trns = sip.withClientTransaction(req) { tx =>
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
