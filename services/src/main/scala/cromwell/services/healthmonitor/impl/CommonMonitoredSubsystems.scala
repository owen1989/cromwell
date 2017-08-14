package cromwell.services.healthmonitor.impl

import cromwell.core.docker.{DockerCliClient, DockerCliKey}
import cromwell.services.SingletonServicesStore
import cromwell.services.healthmonitor.HealthMonitorServiceActor
import cromwell.services.healthmonitor.HealthMonitorServiceActor.{OkStatus, MonitoredSubsystem, SubsystemStatus}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object CommonMonitoredSubsystems extends SingletonServicesStore {
  val DockerHub = MonitoredSubsystem("DockerHub", checkDockerhub)
  val Db = MonitoredSubsystem("SQL Database", checkDb)

  // FIXME: Non-implicit ec solves issue w/ Eta expansion & functions not having implicit args, but requires impliciting in body hrm
  private def checkDockerhub(ec: ExecutionContext): Future[SubsystemStatus] = {
    implicit val iec = ec
    Future {
      val alpine = DockerCliKey("alpine", "latest") // FIXME: perhaps a clone of alpine in our dockerhub?

      val res = for {
        p <- DockerCliClient.pull(alpine)
        r <- DockerCliClient.rmi(alpine)
      } yield r

      res match {
        case Success(_) => OkStatus
        case Failure(e) => HealthMonitorServiceActor.failedStatus(e.getLocalizedMessage) // FIXME: Seems unecessary?
      }
    }
  }

  private def checkDb(ec: ExecutionContext): Future[SubsystemStatus] = {
    implicit val iec = ec
    databaseInterface.queryDockerHashStoreEntries("DOESNOTEXIST") map { _ => OkStatus }
  }
}
