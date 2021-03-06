package org.jetbrains.bsp.project.test.environment

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.extensions.ExtensionPointName
import scala.collection.JavaConverters._

sealed trait ExecutionEnvironmentType

object ExecutionEnvironmentType {
  case object TEST extends ExecutionEnvironmentType
  case object RUN extends ExecutionEnvironmentType
}



object BspEnvironmentRunnerExtension {
  val EP_NAME: ExtensionPointName[BspEnvironmentRunnerExtension] =
    ExtensionPointName.create("com.intellij.bspEnvironmentRunnerExtension")

  def isSupported(config: RunConfiguration): Boolean =
    extensions.exists(_.runConfigurationSupported(config))

  def getClassExtractor(runConfiguration: RunConfiguration): Option[BspEnvironmentRunnerExtension] =
    extensions.find(_.runConfigurationSupported(runConfiguration))

  private def extensions = {
    EP_NAME.getExtensionList().asScala.iterator
  }
}

/**
 * Interface for `com.intellij.bspEnvironmentRunnerExtension`
 */
trait BspEnvironmentRunnerExtension {
  /**
   * BSP run/test environment may differ, depending on the BSP target that is chosen.
   * Typically, IntelliJ prompts the user for selection of the BSP target, but in some cases,
   * where the runner is running a certain set of test classes/main classes it may be possible to
   * infer BSP target. This endpoint should return a list of these classes.
   */
  def classes(config: RunConfiguration) : Option[Seq[String]]

  /**
   * Returns true if this extension supports the config
   */
  def runConfigurationSupported(config: RunConfiguration): Boolean

  /**
   * BSP protocol supports two kinds of exection environments: Test Environment and Run Environment.
   * This endpoint is to choose which kind should be taken for the supported run configuration.
   */
  def environmentType: ExecutionEnvironmentType
}
