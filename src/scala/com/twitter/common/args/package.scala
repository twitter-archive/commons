package com.twitter.common

import annotation.target.getter


package object args {
  /**
   * Meta-annotate the CmdLine annotation with the @getter
   * annotation. This indicates that the annotation should
   * propagate to the @getter of annotated field of a case
   * class. The Flag.apply() method searches getters for
   * annotations.
   */
  type Flag = com.twitter.common.args.CmdLine @getter
}