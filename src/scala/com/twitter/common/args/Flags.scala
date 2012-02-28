package com.twitter.common.args

import com.google.common.base.Optional
import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer
import com.twitter.common.args._

object Flags {
  private[this] val Assignable = "^([\\S]+)_\\$eq$".r
  case class Test(field: String)

  def apply[A <: AnyRef](args: Seq[String])(implicit manifest: Manifest[A]): A = {
    println(manifest.erasure.getConstructors().toList)
    val struct = manifest.erasure.newInstance.asInstanceOf[A]

    val optionsFromAssignableMethods = ArrayBuffer[OptionInfo[_]]()
    for (method <- struct.getClass.getMethods if method.getParameterTypes.size == 1) {
      method.getName match {
        case Assignable(fieldName) =>
          val clazz = boxType(method.getParameterTypes.head)
          val optionInfo: OptionInfo[_] = OptionInfo.create(fieldName, "no help for you", "", clazz)
          optionsFromAssignableMethods += optionInfo
        case _ => // Not a method we inject
      }
    }
    val argumentInfo = new Args.ArgumentInfo(Optional.absent[PositionalInfo[_]],
      asJavaIterable(optionsFromAssignableMethods))
    new ArgScanner().parse(argumentInfo, args)

    optionsFromAssignableMethods foreach { option =>
      val arg = option.getArg
      if (arg.hasAppliedValue)
        struct.getClass.getMethod(option.getName + "_$eq", unboxType(option.getType.getRawType))
            .invoke(struct, option.getArg.get.asInstanceOf[Object])
    }
    struct
  }

  private[this] def boxType(clazz: Class[_]): Class[_] = {
    clazz match {
      case c if c == classOf[Int] => classOf[java.lang.Integer]
      case _ => clazz
    }
  }

  private[this] def unboxType(clazz: Class[_]): Class[_] = {
    clazz match {
      case c if c == classOf[java.lang.Integer] => classOf[Int]
      case _ => clazz
    }
  }
}
