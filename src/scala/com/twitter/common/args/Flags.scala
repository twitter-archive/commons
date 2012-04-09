package com.twitter.common.args

import com.google.common.base.Optional
import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer
import com.twitter.common.args._
import tools.scalap.scalax.rules.scalasig._
import java.lang.{Class, String}
import scala.annotation.target.field

object Flags {
  private[this] val Copy = "copy"
  private[this] val Copy$default$ = "copy$default$"
  private[this] val DefaultHelp = "no help for you"

  def apply[A <: AnyRef](caseInstance: A, args: Seq[String]): A = {
    val caseClass = caseInstance.getClass
    val fields = fieldsFor(caseClass)
    val copyMethod = copyMethodFor(caseClass)

    val optionInfos = fields map { case (name, clazz) =>
      val prefix = ""
      val flagAnnotation = Option(caseClass.getMethod(name).getAnnotation(classOf[CmdLine]))
      OptionInfo.create(
        flagAnnotation.map(_.name).getOrElse(name),
        flagAnnotation.map(_.name).getOrElse(DefaultHelp),
        prefix,
        boxType(clazz))
    }
    val argumentInfo = new Args.ArgumentInfo(
      Optional.absent[PositionalInfo[_]],
      asJavaIterable(optionInfos))
    new ArgScanner().parse(argumentInfo, args)

    val parametersToCopyMethod = optionInfos.zipWithIndex.map { case (optionInfo, i) =>
      val arg = optionInfo.getArg
      if (arg.hasAppliedValue)
        arg.get.asInstanceOf[Object]
      else
        caseClass.getMethod(Copy$default$ + (i + 1)).invoke(caseInstance)
    }
    copyMethod.invoke(caseInstance, parametersToCopyMethod: _*).asInstanceOf[A]
  }


  private[this] def boxType(clazz: Class[_]): Class[_] = {
    clazz match {
      case c if c == classOf[Int] => classOf[java.lang.Integer]
      case _ => clazz
    }
  }

  private[this] def copyMethodFor(clazz: Class[_]) = {
    clazz.getMethods.find(_.getName == Copy).getOrElse(
      error("Cannot find copy method for class " + clazz.getName)
    )
  }

  /**
   * Portions of this code are copied from
   * http://stackoverflow.com/questions/6282464/in-scala-how-can-i-programmatically-determine-the-name-of-the-fields-of-a-case
   */
  private[this] def fieldsFor(clazz: Class[_]): Seq[(String, Class[_])] = {
    val rootClass = {
      var currentClass = clazz
      while (currentClass.getEnclosingClass ne null) currentClass = currentClass.getEnclosingClass
      currentClass
    }

    val sig = ScalaSigParser.parse(rootClass).getOrElse(
      error("No ScalaSig for class " + rootClass.getName + ", make sure it is a top-level case class"))
    val tableSize = sig.table.size

    val classSymbolIndex = (0 until tableSize).find { i =>
      sig.parseEntry(i) match {
        case c @ ClassSymbol(SymbolInfo(name, _, _, _, _, _), _)
          if c.isCase && c.path == clazz.getCanonicalName => true
        case _ => false
      }
    }.getOrElse(error("Class " + rootClass.getName + " is not a case class"))
    val classSymbol = sig.parseEntry(classSymbolIndex).asInstanceOf[ClassSymbol]

    val copyMethod = copyMethodFor(clazz)

    val copyIndex = ((classSymbolIndex + 1) until tableSize).find { i =>
      sig.parseEntry(i) match {
        case m @ MethodSymbol(SymbolInfo(Copy, owner, _, _, _, _), _) => owner match {
          case sym: SymbolInfoSymbol if sym.index == classSymbol.index => true
          case _ => false
        }
        case _ => false
      }
    }.getOrElse(error("Cannot find copy method entry in ScalaSig for class " + rootClass.getName))

    val paramsListBuilder = List.newBuilder[String]
    for (i <- (copyIndex + 1) until tableSize) {
      sig.parseEntry(i) match {
        case MethodSymbol(SymbolInfo(name, owner, _, _, _, _), _) => owner match {
          case sym: SymbolInfoSymbol if sym.index == copyIndex => paramsListBuilder += name
          case _ =>
        }
        case _ =>
      }
    }
    paramsListBuilder.result zip copyMethod.getParameterTypes
  }
}
