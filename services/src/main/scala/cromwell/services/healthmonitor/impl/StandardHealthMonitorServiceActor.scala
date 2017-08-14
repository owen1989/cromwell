package cromwell.services.healthmonitor.impl

import com.typesafe.config.Config
import cromwell.services.healthmonitor.HealthMonitorServiceActor
import cromwell.services.healthmonitor.HealthMonitorServiceActor.MonitoredSubsystem
import CommonMonitoredSubsystems._

class StandardHealthMonitorServiceActor(serviceConfig: Config, globalConfig: Config) extends HealthMonitorServiceActor {
  override lazy val subsystems: List[MonitoredSubsystem] = List(DockerHub, Db)
}
