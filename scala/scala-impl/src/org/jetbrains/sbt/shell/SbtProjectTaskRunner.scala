package org.jetbrains.sbt.shell

import java.io.File
import java.util
import java.util.UUID

import com.intellij.build.events.impl._
import com.intellij.build.events.{SuccessResult, Warning}
import com.intellij.build.{BuildViewManager, DefaultBuildDescriptor, events}
import com.intellij.compiler.impl.CompilerUtil
import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.compiler.ex.CompilerPathsEx
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.{ExternalSystemUtil, ExternalSystemApiUtil => ES}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.{Module, ModuleType}
import com.intellij.openapi.progress.{PerformInBackgroundOption, ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.task._
import org.jetbrains.annotations.Nullable
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.module.SbtModuleType
import org.jetbrains.sbt.settings.SbtSystemSettings
import org.jetbrains.sbt.shell.SbtShellCommunication._

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

/**
  * Created by jast on 2016-11-25.
  */
class SbtProjectTaskRunner extends ProjectTaskRunner {

  // will override the usual jps build thingies
  override def canRun(projectTask: ProjectTask): Boolean = projectTask match {
    case task: ModuleBuildTask =>
      val module = task.getModule
      ModuleType.get(module) match {
          // TODO Android AARs are currently imported as modules. need a way to filter them away before building
        case _: SbtModuleType =>
          // SbtModuleType actually denotes `-build` modules, which are not part of the regular build
          false
        case _ =>
          val project = task.getModule.getProject
          val projectSettings = SbtSystemSettings.getInstance(project).getLinkedProjectSettings(module)

          projectSettings.exists(_.useSbtShell) &&
          ES.isExternalSystemAwareModule(SbtProjectSystem.Id, module)
      }
    case _: ArtifactBuildTask =>
      // TODO should sbt handle this?
      false
    case _: ExecuteRunConfigurationTask =>
      // TODO this includes tests (and what else?). sbt should handle it and test output should be parsed
      false
    case _ => false
  }

  override def run(project: Project,
                   context: ProjectTaskContext,
                   callback: ProjectTaskNotification,
                   tasks: util.Collection[_ <: ProjectTask]): Unit = {

    val validTasks = tasks.asScala.collect {
      case task: ModuleBuildTask => task
    }

    // the "build" button in IDEA always runs the build for all individual modules,
    // and may work differently than just calling the products task from the main module in sbt
    val moduleCommands = validTasks.flatMap(buildCommands)
    val modules = validTasks.map(_.getModule)

    // don't run anything if there's no module to run a build for
    // TODO user feedback
    if (moduleCommands.nonEmpty) {

      val command =
        if (moduleCommands.size == 1) moduleCommands.head
        else moduleCommands.mkString("all ", " ", "")

      FileDocumentManager.getInstance().saveAllDocuments()

      // run this as a task (which blocks a thread) because it seems non-trivial to just update indicators asynchronously?
      val task = new CommandTask(project, modules.toArray, command, Option(callback))
      ProgressManager.getInstance().run(task)
    }
  }

  private def buildCommands(task: ModuleBuildTask): Seq[String] = {
    // TODO sensible way to find out what scopes to run it for besides compile and test?
    // TODO make tasks should be user-configurable
    SbtUtil.getSbtModuleData(task.getModule).toSeq.flatMap { sbtModuleData =>
      val scope = SbtUtil.makeSbtProjectId(sbtModuleData)
      // `products` task is a little more general than just `compile`
      Seq(s"$scope/products", s"$scope/test:products")
    }
  }

  @Nullable
  override def createExecutionEnvironment(project: Project,
                                          task: ExecuteRunConfigurationTask,
                                          executor: Executor): ExecutionEnvironment = {

    val taskSettings = new ExternalSystemTaskExecutionSettings
    val executorId = Option(executor).map(_.getId).getOrElse(DefaultRunExecutor.EXECUTOR_ID)

    ExternalSystemUtil.createExecutionEnvironment(
      project,
      SbtProjectSystem.Id,
      taskSettings, executorId
    )
  }

}

private class CommandTask(project: Project, modules: Array[Module], command: String, callbackOpt: Option[ProjectTaskNotification]) extends
  Task.Backgroundable(project, "sbt build", false, PerformInBackgroundOption.ALWAYS_BACKGROUND) {

  import CommandTask._

  override def run(indicator: ProgressIndicator): Unit = {

    val shell = SbtShellCommunication.forProject(project)
    val viewManager = ServiceManager.getService(project, classOf[BuildViewManager])

    val taskId = UUID.randomUUID()
    val buildDescriptor = new DefaultBuildDescriptor(taskId, "sbt build", project.getBasePath, System.currentTimeMillis())
    val startEvent = new StartBuildEventImpl(buildDescriptor, "queued sbt build ...")

    viewManager.onEvent(startEvent)


    // TODO build events instead of indicator
    val resultAggregator: (BuildMessages,ShellEvent) => BuildMessages = { (data,event) =>
      event match {
        case TaskStart =>
          indicator.setIndeterminate(true)
          indicator.setFraction(0.1)
          indicator.setText("building ...")
        case TaskComplete =>
          indicator.setText("")
        case ErrorWaitForInput =>
          // TODO should be a build error, but can only actually happen during reload anyway
        case Output(text) =>
          indicator.setText2(text)
      }

      taskResultAggregator(data,event)
    }

    val failedResult = new ProjectTaskResult(true, 1, 0)

    // TODO consider running module build tasks separately
    // may require collecting results individually and aggregating
    val commandFuture = shell.command(command, BuildMessages.empty, resultAggregator, showShell = true)

    // build effects
    commandFuture
      .andThen {
        case _ => refreshRoots(modules, indicator)
      }

    // handle callback
    commandFuture
      .map(messages => new ProjectTaskResult(messages.aborted, messages.errors.size, messages.warnings.size))
      .andThen {
        case Success(taskResult) => callbackOpt.foreach(_.finished(taskResult))
        case Failure(_) => callbackOpt.foreach(_.finished(failedResult))
      }


    // build state reporting
    commandFuture
      .andThen {
        case Success(messages) =>
          val result =
            if (messages.errors.isEmpty)
              new SuccessResultImpl
            else {
              val fails: util.List[events.Failure] = messages.errors.map(msg => new FailureImpl(msg, msg): events.Failure).asJava
              new FailureResultImpl(fails)
            }

          val successEvent =
            new FinishBuildEventImpl(taskId, null, System.currentTimeMillis(), "sbt build completed", result)
          viewManager.onEvent(successEvent)
        case Failure(err) =>
          val failureResult = new FailureResultImpl(err)
          val failureEvent =
            new FinishBuildEventImpl(taskId, null, System.currentTimeMillis(), "sbt build failed", failureResult)
          viewManager.onEvent(failureEvent)
      }



    // block thread to make indicator available :(
    Await.ready(commandFuture, Duration.Inf)
  }

  // remove this if/when external system handles this refresh on its own
  private def refreshRoots(modules: Array[Module], indicator: ProgressIndicator): Unit = {
    indicator.setText("Synchronizing output directories...")

    // simply refresh all the source roots to catch any generated files -- this MAY have a performance impact
    // in which case it might be necessary to receive the generated sources directly from sbt and refresh them (see BuildManager)
    val info = ProjectDataManager.getInstance().getExternalProjectData(project,SbtProjectSystem.Id, project.getBasePath)
    val allSourceRoots = ES.findAllRecursively(info.getExternalProjectStructure, ProjectKeys.CONTENT_ROOT)
    val generatedSourceRoots = allSourceRoots.asScala.flatMap { node =>
      val data = node.getData
      // sbt-side generated sources are still imported as regular sources
      val generated = data.getPaths(ExternalSystemSourceType.SOURCE_GENERATED).asScala
      val regular = data.getPaths(ExternalSystemSourceType.SOURCE).asScala
      generated ++ regular
    }.map(_.getPath).toSeq.distinct

    val outputRoots = CompilerPathsEx.getOutputPaths(modules)
    val toRefresh = generatedSourceRoots ++ outputRoots

    CompilerUtil.refreshOutputRoots(toRefresh.asJavaCollection)
    val toRefreshFiles = toRefresh.map(new File(_)).asJava
    LocalFileSystem.getInstance().refreshIoFiles(toRefreshFiles, true, true, null)

    indicator.setText("")
  }
}

object CommandTask {

  // some code duplication here with SbtStructureDump
  private val WARN_PREFIX = "[warn]"
  private val ERROR_PREFIX = "[error]"

  private val taskResultAggregator: EventAggregator[BuildMessages] = (result, event) =>
    event match {
      case TaskStart => result
      case TaskComplete => result
      case ErrorWaitForInput =>
        result
          .addError("Error reloading project.")
          .abort // build should be aborted if this happens (but it shouldn't happen)
      case Output(text) =>
        if (text startsWith ERROR_PREFIX)
          result.addError(text.stripPrefix(ERROR_PREFIX))
        else if (text startsWith "[warning]")
          result.addWarning(text.stripPrefix(WARN_PREFIX))
        else result
    }
}

private case class BuildMessages(warnings: Seq[String], errors: Seq[String], log: Seq[String], aborted: Boolean) {
  def appendMessage(text: String): BuildMessages = copy(log = log :+ text.trim)
  def addError(msg: String): BuildMessages = copy(errors = errors :+ msg.trim)
  def addWarning(msg: String): BuildMessages = copy(warnings = warnings :+ msg.trim)
  def abort: BuildMessages = copy(aborted = true)
  def toTaskResult: ProjectTaskResult = new ProjectTaskResult(aborted, errors.size, warnings.size)
}

private case object BuildMessages {
  def empty = BuildMessages(Vector.empty, Vector.empty, Vector.empty, aborted = false)
}

private case class SbtBuildResult(warnings: Seq[String] = Seq.empty) extends SuccessResult {
  override def isUpToDate = false
  override def getWarnings: util.List[Warning] = warnings.map(SbtWarning.apply(_) : Warning).asJava
}

private case class SbtWarning(message: String) extends Warning {
  override def getMessage: String = message
  override def getDescription: String = message
}