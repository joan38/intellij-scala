package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.debugger.Scala_2_13
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

/*
 Tests complex interactions between various type-mismatch highlighting features, including:
   https://youtrack.jetbrains.net/issue/SCL-15138 Only highlight initial, not derivative errors
   https://youtrack.jetbrains.net/issue/SCL-14778 Better highlighting of compound expressions
   https://youtrack.jetbrains.net/issue/SCL-14777 Block expression: underline final expression instead of closing brace
   https://youtrack.jetbrains.net/issue/SCL-15250 Use inlay type ascription to indicate type mismatch
   https://youtrack.jetbrains.net/issue/SCL-15481 Type mismatch: fine-grained diff
   https://youtrack.jetbrains.net/issue/SCL-15544 Type ascription: highlight type, not expression
   https://youtrack.jetbrains.net/issue/SCL-15571 Type mismatch errors: widen literal types when the value is of no importance
   https://youtrack.jetbrains.net/issue/SCL-15592 Method / constructor invocation: highlight only a single kind of error
   https://youtrack.jetbrains.net/issue/SCL-15594 Don't highlight arguments when there are multiple inapplicable overloaded methods
 */

class TypeMismatchHighlightingTest extends ScalaHighlightingTestBase {

  override implicit val version = Scala_2_13

  override protected def withHints = true

  private var savedIsTypeMismatchHints: Boolean = _

  override protected def setUp(): Unit = {
    super.setUp()
    savedIsTypeMismatchHints = ScalaProjectSettings.in(project).isTypeMismatchHints
    ScalaProjectSettings.in(project).setTypeMismatchHints(true)
  }

  override def tearDown(): Unit = {
    ScalaProjectSettings.in(project).setTypeMismatchHints(savedIsTypeMismatchHints)
    super.tearDown()
  }

  // Type ascription

  // SCL-15544
  def testTypeAscriptionOk(): Unit = {
    assertMessages(errorsFromScalaCode("1: Int"))()
  }

  // Highlight type ascription differently from type mismatch (handled in ScTypedExpressionAnnotator), SCL-15544
  def testTypeAscriptionError(): Unit = {
    assertMessages(errorsFromScalaCode("(): Int"))(Error("Int", "Cannot upcast Unit to Int"))
  }

  // Widen literal type when non-literal type is ascribed (handled in ScTypedExpressionAnnotator), SCL-15571
  def testTypeAscriptionErrorWiden(): Unit = {
    assertMessages(errorsFromScalaCode("true: Int"))(Error("Int", "Cannot upcast Boolean to Int"))
  }

  // Don't widen literal type when literal type is ascribed (handled in ScTypedExpressionAnnotator), SCL-15571
  def testTypeAscriptionErrorNotWiden(): Unit = {
    assertMessages(errorsFromScalaCode("1: 2"))(Error("2", "Cannot upcast 1 to 2"))
  }

  // Fine-grained type ascription diff, SCL-15544, SCL-15481
  def testTypeAscriptionErrorDiff(): Unit = {
    assertMessages(errorsFromScalaCode("Some(1): Option[String]"))(Error("String", "Cannot upcast Some[Int] to Option[String]"))
  }

  // Expected type & type ascription

  // SCL-15544
  def testTypeMismatchAndTypeAscriptionOk(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int = 1: Int"))()
  }

  // When present, highlight type ascription, not expression, SCL-15544
  def testTypeMismatchAndTypeAscriptionError(): Unit = {
    // TODO unify the message
    assertMessages(errorsFromScalaCode("val v: Int = \"foo\": String"))(Error("String", "Expression of type String doesn't conform to expected type Int"))
  }

  // Widen type when non-literal type is expected, SCL-15571
  def testTypeMismatchAndTypeAscriptionErrorWiden(): Unit = {
    // TODO unify the message
    assertMessages(errorsFromScalaCode("val v: Int = true: true"))(Error("true", "Expression of type Boolean doesn't conform to expected type Int"))
  }

  // Don't widen type when literal type is expected, SCL-15571
  def testTypeMismatchAndTypeAscriptionErrorNotWiden(): Unit = {
    // TODO unify the message
    assertMessages(errorsFromScalaCode("val v: 1 = 2: 2"))(Error("2", "Expression of type 2 doesn't conform to expected type 1"))
  }

  // Don't narrow type when non-literal type is ascribed but literal type is expected, SCL-15571
  def testTypeMismatchAndTypeAscriptionErrorNotNarrow(): Unit = {
    // TODO unify the message
    assertMessages(errorsFromScalaCode("val v: 1 = 2: Int"))(Error("Int", "Expression of type Int doesn't conform to expected type 1"))
  }

  // TODO (ScExpressionAnnotator)
  // Fine-grained type mismatch diff, SCL-15544, SCL-15481
//  def testTypeMismatchAndTypeAscriptionErrorDiff(): Unit = {
//    assertMessages(errorsFromScalaCode("val v: Option[Int] = Some(\"foo\"): Some[String]"))(Error("String", "Expression of type String doesn't conform to expected type Int")) // TODO unify the message
//  }

  // Don't show additional type mismatch when there's an error in type ascription (handled in ScTypedExpressionAnnotator), SCL-15544
  def testTypeMismatchAndTypeAscriptionInnerError(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int = 1: String"))(Error("String", "Cannot upcast Int to String"))
  }

  // Type mismatch hint

  // Use type ascription to show type mismatch, SCL-15250 (invisible Error is added for statusbar message / scollbar mark / quick-fix)
  def testTypeMismatchHint(): Unit = {
    assertMessages(errorsFromScalaCode("val v: String = ()"))(Hint("()", ": Unit"),
      Error("()", "Expression of type Unit doesn't conform to expected type String"))
  }

  // Widen literal type when non-literal type is expected, SCL-15571
  def testTypeMismatchHintWiden(): Unit = {
    assertMessages(errorsFromScalaCode("val v: String = 1"))(Hint("1", ": Int"),
      Error("1", "Expression of type Int doesn't conform to expected type String"))
  }

  // Don't widen literal type when literal type is expected, SCL-15571
  def testTypeMismatchHintNotWiden(): Unit = {
    assertMessages(errorsFromScalaCode("val v: 1 = 2"))(Hint("2", ": 2"),
      Error("2", "Expression of type 2 doesn't conform to expected type 1"))
  }

  // Add parentheses when needed
  def testTypeMismatchHintParentheses(): Unit = {
    assertMessages(errorsFromScalaCode("val v: String = 1 + 2"))(Hint("1 + 2", "("), Hint("1 + 2", "): Int"),
      Error("1 + 2", "Expression of type Int doesn't conform to expected type String"))
  }
}
