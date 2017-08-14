package cromwell.services.healthmonitor.impl

import cromwell.services.healthmonitor.HealthMonitorServiceActor
import CommonMonitoredSubsystems._

/*
  Checks:

  PAPI (if backend exists)
  GCS (if filesystem exists)
 */

class WorkbenchHealthMonitorServiceActor extends HealthMonitorServiceActor {
  override lazy val subsystems = List(DockerHub, Db)
}

object WorkbenchHealthMonitorServiceActor {

}