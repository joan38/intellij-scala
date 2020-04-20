package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.PropSpecGenerator

trait PropSpecSingleTestTest extends PropSpecGenerator {

  val propSpecTestPath = List("[root]", propSpecClassName, "Single tests should run")
  val propSpecTestTaggedPath = List("[root]", propSpecClassName, "tagged")

  def testPropSpec(): Unit = {
    runTestByLocation2(5, 5, propSpecFileName,
      assertConfigAndSettings(_, propSpecClassName, "Single tests should run"),
      root => checkResultTreeHasExactNamedPath(root, propSpecTestPath:_*) &&
          checkResultTreeDoesNotHaveNodes(root, "other tests should not run")
    )
  }

  def testTaggedPropSpec(): Unit = {
    runTestByLocation2(12, 5, propSpecFileName,
      assertConfigAndSettings(_, propSpecClassName, "tagged"),
      root => checkResultTreeHasExactNamedPath(root, propSpecTestTaggedPath:_*) &&
        checkResultTreeDoesNotHaveNodes(root, "other tests should not run")
    )
  }
}
