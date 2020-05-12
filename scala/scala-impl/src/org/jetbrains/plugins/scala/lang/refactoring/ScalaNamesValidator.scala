package org.jetbrains.plugins.scala.lang.refactoring

import com.intellij.lang.LanguageNamesValidation
import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil.isEmpty
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.{KEYWORDS, tIDENTIFIER}
import org.jetbrains.plugins.scala.util.UnloadableThreadLocal

class ScalaNamesValidator extends NamesValidator {

  private val lexerCache = UnloadableThreadLocal(new ScalaLexer(false, null))

  private val keywordNames: Set[String] = KEYWORDS.getTypes
    .map(_.toString)
    .toSet

  override def isIdentifier(name: String, project: Project): Boolean = {
    if (isEmpty(name)) return false

    val lexer = lexerCache.value
    lexer.start(name)

    if (lexer.getTokenType != tIDENTIFIER) return false
    lexer.advance()
    lexer.getTokenType == null
  }

  override def isKeyword(name: String, project: Project): Boolean =
    keywordNames.contains(name)
}

object ScalaNamesValidator {

  def isKeyword(name: String): Boolean =
    validator.isKeyword(name, null)

  def isIdentifier(name: String): Boolean =
    validator.isIdentifier(name, null)

  private def validator =
    LanguageNamesValidation.INSTANCE.forLanguage(ScalaLanguage.INSTANCE)
}