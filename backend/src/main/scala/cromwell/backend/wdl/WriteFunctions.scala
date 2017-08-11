package cromwell.backend.wdl

import cromwell.core.path.Path
import wdl4s.wdl.expression.WdlStandardLibraryFunctions
import wdl4s.wdl.values._

import scala.util.Try

trait WriteFunctions { this: WdlStandardLibraryFunctions =>

  /**
    * Directory that will be used to write files.
    */
  def writeDirectory: Path

  private lazy val _writeDirectory = writeDirectory.createPermissionedDirectories()

  def writeTempFile(path: String,prefix: String,suffix: String,content: String): String = throw new NotImplementedError("This method is not used anywhere and should be removed")

  override def writeFile(path: String, content: String): Try[WdlFile] = {
    val file = _writeDirectory / path
    Try {
      if (file.notExists) file.write(content)
    } map { _ =>
      WdlFile(file.pathAsString)
    }
  }
}
