package org.jetbrains.plugins.scala.testingSupport.specs2

/**
 * @author Roman.Shein
 * @since 03.07.2015.
 */
abstract class Specs2RegExpTestNameTest extends Specs2TestCase {
  protected val regExpClassName = "SpecsRegExpTest"
  protected val regExpFileName = regExpClassName + ".scala"

  addSourceFile(regExpFileName,
    """
      |import org.specs2.mutable.Specification
      |
      |class SpecsRegExpTest extends Specification {
      |  "The RegExpTest" should {
      |    "testtesttest" in {
      |      1 mustEqual 1
      |    }
      |
      |    "test" ! { success }
      |
      |    "testtest" >> { success }
      |  }
      |
      |  "First" should {
      |    "run" ! { success }
      |  }
      |
      |  "Second" should {
      |    "run" ! { success }
      |  }
      |}
    """.stripMargin.trim)

  def testInnerMost(): Unit = {
    runTestByLocation2(8, 10, regExpFileName,
      assertConfigAndSettings(_, regExpClassName, "test"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", regExpClassName, "The RegExpTest should", "test") &&
        checkResultTreeDoesNotHaveNodes(root, "testtest", "testtesttest")
    )
  }

  def testMiddle(): Unit = {
    runTestByLocation2(10, 10, regExpFileName,
      assertConfigAndSettings(_, regExpClassName, "testtest"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", regExpClassName, "The RegExpTest should", "testtest") &&
        checkResultTreeDoesNotHaveNodes(root, "test", "testtesttest"))
  }

  def testOuterMost(): Unit = {
    runTestByLocation2(4, 10, regExpFileName,
      assertConfigAndSettings(_, regExpClassName, "testtesttest"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", regExpClassName, "The RegExpTest should", "testtesttest") &&
        checkResultTreeDoesNotHaveNodes(root, "test", "testtest"))
  }

  //TODO: enable the test once I find a way to run different tests with same description in specs2
  def __IGNORE_testDifferentScopes(): Unit = {
    runTestByLocation2(14, 10, regExpFileName,
      assertConfigAndSettings(_, regExpClassName, "run"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", regExpClassName, "First should", "run") &&
        checkResultTreeDoesNotHaveNodes(root, "Second should"))

    runTestByLocation2(18, 10, regExpFileName,
      assertConfigAndSettings(_, regExpClassName, "run"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", regExpClassName, "Second should", "run") &&
        checkResultTreeDoesNotHaveNodes(root, "First should"))
  }
}
