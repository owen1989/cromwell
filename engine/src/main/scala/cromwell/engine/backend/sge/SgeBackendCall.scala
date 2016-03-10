package cromwell.engine.backend.sge

import cromwell.engine.AbortRegistrationFunction
import cromwell.engine.backend.{BackendCall, LocalFileSystemBackendCall, _}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

case class SgeBackendCall(backend: SgeBackend,
                          jobDescriptor: BackendCallJobDescriptor,
                          callAbortRegistrationFunction: Option[AbortRegistrationFunction]) extends BackendCall with LocalFileSystemBackendCall {
  val workflowRootPath = workflowDescriptor.workflowRootPath
  val stdout = callRootPath.resolve("stdout")
  val stderr = callRootPath.resolve("stderr")
  val script = callRootPath.resolve("script.sh")
  val returnCode = callRootPath.resolve("rc")

  def instantiateCommand: Try[String] = {
    val backendInputs = backend.adjustInputPaths(this)
    call.instantiateCommandLine(backendInputs, callEngineFunctions)
  }

  override def execute(implicit ec: ExecutionContext) = backend.execute(this)

  override def poll(previous: ExecutionHandle)(implicit ec: ExecutionContext) = Future.successful(previous)

  override def useCachedCall(avoidedTo: BackendCall)(implicit ec: ExecutionContext): Future[ExecutionHandle] =
    backend.useCachedCall(avoidedTo.asInstanceOf[SgeBackendCall], this)

  override def stdoutStderr: CallLogs = backend.stdoutStderr(this)
}