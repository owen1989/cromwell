package cromwell.services.healthmonitor

import java.util.concurrent.TimeoutException

import akka.actor.{Actor, Cancellable}
import akka.pattern.{after, pipe}
import cromwell.util.GracefulShutdownHelper.ShutdownCommand
//import cats._
//import cats.implicits._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.language.postfixOps
import HealthMonitorServiceActor._
import cromwell.services.ServiceRegistryActor.ServiceRegistryMessage

trait HealthMonitorServiceActor extends Actor with LazyLogging {
  lazy val subsystems: List[MonitoredSubsystem] = List.empty[MonitoredSubsystem] // FIXME: :'(

  implicit val ec: ExecutionContext = context.system.dispatcher

  val futureTimeout: FiniteDuration = DefaultFutureTimeout
  val staleThreshold: FiniteDuration = DefaultStaleThreshold

  logger.info("Starting health monitor...")
  val checkTick: Cancellable = context.system.scheduler.schedule(10 seconds, 1 minute, self, CheckAll)

  override def postStop(): Unit = {
    checkTick.cancel()
    ()
  }

  /**
    * Contains each subsystem status along with a timestamp of when the entry was made.
    * Initialized with unknown status.
    */
  private var data: Map[MonitoredSubsystem, CachedSubsystemStatus] = {
    val now = System.currentTimeMillis
    subsystems.map((_, CachedSubsystemStatus(UnknownStatus, now))).toMap
  }

  override def receive: Receive = {
    case CheckAll => subsystems.foreach(checkSubsystem)
    case Store(subsystem, status) => store(subsystem, status)
    case GetCurrentStatus => sender ! getCurrentStatus
    case ShutdownCommand => context.stop(self) // FIXME: rework service registryto not require all children to be graceful
  }

  private def checkSubsystem(subsystem: MonitoredSubsystem): Unit = {
    val result = subsystem.check(context.dispatcher)
    result.withTimeout(futureTimeout, s"Timed out after ${futureTimeout.toString} waiting for a response from ${subsystem.toString}")
      .recover { case NonFatal(ex) =>
        failedStatus(ex.getMessage)
      } map {
      Store(subsystem, _)
    } pipeTo self

    ()
  }

  private def store(subsystem: MonitoredSubsystem, status: SubsystemStatus): Unit = {
    data = data + ((subsystem, CachedSubsystemStatus(status, System.currentTimeMillis)))
    logger.debug(s"New health monitor state: $data")
  }

  private def getCurrentStatus: StatusCheckResponse = {
    val now = System.currentTimeMillis()
    // Convert any expired statuses to unknown
    val processed = data map {
      case (s, c) if now - c.created > staleThreshold.toMillis => s.name -> UnknownStatus
      case (s, c) => s.name -> c.status
    }

    // overall status is ok iff all subsystems are ok
    val overall = processed.forall(_._2.ok)
    StatusCheckResponse(overall, processed)
  }

  /**
    * A monoid used for combining SubsystemStatuses.
    * Zero is an ok status with no messages.
    * Append uses && on the ok flag, and ++ on the messages.
    */
  // FIXME: Might be needed in checks, see how Rawls is doing things in the google checks
//  private implicit val SubsystemStatusMonoid = new Monoid[SubsystemStatus] {
//    def combine(a: SubsystemStatus, b: SubsystemStatus): SubsystemStatus = {
//      SubsystemStatus(a.ok && b.ok, a.messages |+| b.messages)
//    }
//
//    def empty: SubsystemStatus = OkStatus
//  }

  /**
    * Adds non-blocking timeout support to futures.
    * Example usage:
    * {{{
    *   val future = Future(Thread.sleep(1000*60*60*24*365)) // 1 year
    *   Await.result(future.withTimeout(5 seconds, "Timed out"), 365 days)
    *   // returns in 5 seconds
    * }}}
    */
  private implicit class FutureWithTimeout[A](f: Future[A]) {
    def withTimeout(duration: FiniteDuration, errMsg: String): Future[A] =
      Future.firstCompletedOf(Seq(f, after(duration, context.system.scheduler)(Future.failed(new TimeoutException(errMsg)))))
  }
}

object HealthMonitorServiceActor {
  val DefaultFutureTimeout: FiniteDuration = 1 minute
  val DefaultStaleThreshold: FiniteDuration = 15 minutes

  val OkStatus = SubsystemStatus(true, None)
  val UnknownStatus = SubsystemStatus(false, Some(List("Unknown status")))
  def failedStatus(message: String) = SubsystemStatus(false, Some(List(message)))

  final case class MonitoredSubsystem(name: String, check: (ExecutionContext) => Future[SubsystemStatus])
  final case class SubsystemStatus(ok: Boolean, messages: Option[List[String]])
  final case class CachedSubsystemStatus(status: SubsystemStatus, created: Long) // created is time in millis when status was captured

  sealed abstract class HealthMonitorServiceActorRequest
  case object CheckAll extends HealthMonitorServiceActorRequest
  final case class Store(subsystem: MonitoredSubsystem, status: SubsystemStatus) extends HealthMonitorServiceActorRequest
  case object GetCurrentStatus extends HealthMonitorServiceActorRequest with ServiceRegistryMessage { override val serviceName = "HealthMonitor" }

  sealed abstract class HealthMonitorServiceActorResponse
  final case class StatusCheckResponse(ok: Boolean, systems: Map[String, SubsystemStatus]) extends HealthMonitorServiceActorResponse
}