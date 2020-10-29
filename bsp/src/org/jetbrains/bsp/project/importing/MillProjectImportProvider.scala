package org.jetbrains.bsp.project.importing

import java.io.File
import org.jetbrains.plugins.scala.build.BuildMessages
import scala.io.Source
import scala.sys.process._
import scala.util.{Failure, Try}

object MillProjectImportProvider {
  def canImport(workspace: File): Boolean =
    Option(workspace) match {
      case Some(directory) if directory.isDirectory => isBspCompatible(directory) || isLegacyBspCompatible(directory)
      case _ => false
    }

  def bspInstall(workspace: File): Try[BuildMessages] = {
    val work =
      if (isBspCompatible(workspace)) Try(Process("./mill mill.bsp.BSP/install", workspace).!!)
      else if (isLegacyBspCompatible(workspace)) Try(Process("./mill -i mill.contrib.BSP/install", workspace).!!)
      else Failure(new IllegalStateException("Unable to install BSP as this is not a Mill project"))

    work.transform(
      _ => Try(BuildMessages.empty.status(BuildMessages.OK)),
      e => Try(BuildMessages.empty.status(BuildMessages.Error).addError(e.getMessage))
    )
  }

  private def isBspCompatible(workspace: File) = workspace.listFiles().exists { buildScript =>
    !buildScript.isDirectory && buildScript.getName == "mill" &&
      read(buildScript)(_.getLines().exists(!_.matches("""^.*(0\.8\.0|0\.7.+|0\.6.+)$""")))
  }

  // Legacy Mill =< 0.8.0
  private def isLegacyBspCompatible(workspace: File) = workspace.listFiles().exists { buildScript =>
    !buildScript.isDirectory && buildScript.getName == "build.sc" &&
      read(buildScript)(_.getLines().contains("import $ivy.`com.lihaoyi::mill-contrib-bsp:$MILL_VERSION`"))
  }

  // Replace this by https://www.scala-lang.org/api/current/scala/util/Using$.html once on Scala 2.13
  private def read[T](file: File)(f: Source => T) = {
    val source = Source.fromFile(file)
    try f(source)
    finally source.close()
  }
}
