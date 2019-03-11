package org.jetbrains.plugins.scala.findUsages.compilerReferences
package settings

import com.intellij.openapi.components.{PersistentStateComponent, ServiceManager, State, Storage}
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.plugins.scala.extensions.BooleanExt

import scala.beans.BeanProperty

@State(
  name     = "CompilerIndicesSbtSettings",
  storages = Array(new Storage("compiler_indices_sbt.xml"))
)
class CompilerIndicesSbtSettings extends PersistentStateComponent[CompilerIndicesSbtSettings] {
  //TODO: better instruction on how to set port in sbt
  @BeanProperty var sbtConnectionPort: Int     = 65337
  @BeanProperty var usePredefinedPort: Boolean = false

  def sbtPort: Int = usePredefinedPort.fold(sbtConnectionPort, 0)

  override def getState: CompilerIndicesSbtSettings = this
  override def loadState(state: CompilerIndicesSbtSettings): Unit = XmlSerializerUtil.copyBean(state, this)
}

object CompilerIndicesSbtSettings {
  def apply(): CompilerIndicesSbtSettings = ServiceManager.getService(classOf[CompilerIndicesSbtSettings])
}
