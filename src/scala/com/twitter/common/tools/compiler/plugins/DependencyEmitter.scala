// =================================================================================================
// Copyright 2012 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this work except in compliance with the License.
// You may obtain a copy of the License in the LICENSE file, or at:
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.tools.compiler.plugins

import scala.collection.mutable
import scala.compat.Platform
import scala.io.Codec

import tools.nsc.{Global, Phase}
import tools.nsc.plugins.{Plugin, PluginComponent}
import tools.nsc.symtab.Flags
import tools.nsc.io.{File, Path}

/**
 * A plugin that tracks dependencies from source files to class files.
 *
 * <p>Stores dependencies in a line oriented plain text format where each line has the following
 * format:
 * <pre>
 * [source file path] -&gt; [class file path]
 * </pre>
 *
 * <p>There may be multiple lines per source file if the file contains multiple top level classes,
 * inner classes, or other symbols that map to classes in bytecode.  All paths are normalized to be
 * relative to the classfile output directory.
 *
 * <p>Requires the flag <pre>-P:depemitter:file:&lt;file&gt;</pre> be passed when installed.
 *
 * @param global The compiler instance this plugin is installed in.
 */
class DependencyEmitter(val global: Global) extends Plugin {
  // Required Plugin boilerplate.
  val name = "depemitter"
  val description = "Emits a mapping from scala sources files to classfiles."
  val components = List[PluginComponent](Component)

  // The column formatting here is deliberate.  Unfortunately scalac just expects you to follow the
  // column convention instead of taking flag and description separately and then doing the
  // formatting itself.  The flag must start in column 3 and the description in column 34 as of at
  // least scalac 2.8.1.
  override val optionsHelp: Option[String] =
    Some("  -P:depemitter:file:<file>      The file to emit source to class depencies in.")

  private[this] var dependencyFile: Option[File] = None

  override def processOptions(options: List[String], error: String => Unit) {
    for (option <- options) {
      option.split(":", 2) match {
        case Array("file", path) => dependencyFile = Some(File(Path(path))(Codec(Codec.UTF8)))
        case _ => error("Option %s not understood".format(option))
      }
    }
    if (dependencyFile.isEmpty) {
      error("-P:depemitter:file:<file> is a required flag.")
    }
  }

  private[this] object Component extends PluginComponent {
    // Required PluginComponent boilerplate.
    val global: DependencyEmitter.this.global.type = DependencyEmitter.this.global
    val runsAfter = List[String]("jvm")
    val phaseName = DependencyEmitter.this.name

    import global.{atPhase, currentRun, CompilationUnit, NoSymbol, Symbol}
    import global.icodes.IClass

    def newPhase(prev: Phase): Phase = new EmitDependencies(prev, dependencyFile.get)

    private[this] class EmitDependencies(prev: Phase, depFile: File) extends StdPhase(prev) {
      override def run() {
        if (depFile.exists) {
          global.inform("Reading existing dependency file at %s".format(depFile))
          populateDependencies(depFile.lines())
        }

        super.run

        global.inform("Writing class dependency file to %s".format(depFile))
        val lines = deps.toSeq.flatMap { case (src, classes) =>
          classes.map("%s -> %s%s".format(src, _, Platform.EOL))
        }
        depFile.writeAll(lines: _*)
      }

      private[this] val deps = new mutable.LinkedHashMap[Path, mutable.LinkedHashSet[Path]]()
      private[this] val priorSources = new mutable.HashSet[Path]()

      private[this] def map(src: String, clazz: String) {
        map(Path(src), Path(clazz))
      }

      private[this] def map(src: Path, clazz: Path) {
        val classes = deps.getOrElseUpdate(src, new mutable.LinkedHashSet[Path])
        if (priorSources.remove(src)) {
          // This src was tracked in a prior round - those results should be ditched and just
          // mappings from this compilation run should be used for the source file.
          classes.clear()
        }
        classes.add(clazz)
      }

      private[this] def populateDependencies(lines: Iterator[String]) {
        for ((line, lineNo) <- lines.zipWithIndex) {
          line.split(" -> ") match {
            case Array(src, clazz) => map(src, clazz)
            case _ => {
              global.warning("Failed to parse dependency line %d:\n%s".format(lineNo + 1, line))
            }
          }
        }
        priorSources ++= deps.keys
      }

      private[this] val outputDirs = global.settings.outputDirs

      def apply(unit: CompilationUnit) {
        val src = unit.source.file

        val outdir = Path(outputDirs.outputDirFor(src).absolute.path)
        val from = outdir.relativize(Path(src.absolute.path))

        for (cls: IClass <- unit.icode) {
          val sym = cls.symbol

          val to = classFileRelPath(sym)
          map(from, to)

          if (isTopLevelStaticModule(sym)) {
            map(from, mirrorPath(sym))
          }
        }
      }

      // From here down we lift heavily from tools.nsc.backend.jvm.GenJVM to determine class file
      // names and to detect when a mirror class file needs to be produced for a top level module.
      private[this] def classFileRelPath(sym: Symbol) = {
        val extension = if (isCompanionModule(sym)) { "$.class" } else { ".class" }
        symRelPath(sym, extension)
      }

      private[this] def mirrorPath(sym: Symbol) = symRelPath(sym, ".class")

      private[this] def symRelPath(sym: Symbol, extension: String) = {
        sym.fullName.replace(".", File.separator) + extension
      }

      private[this] def isCompanionModule(sym: Symbol) = {
        sym.hasFlag(Flags.MODULE) && !sym.isMethod && !sym.isImplClass && !sym.hasFlag(Flags.JAVA)
      }

      private[this] def isTopLevelStaticModule(sym: Symbol): Boolean = {
        isTopLevelModule(sym) && isStaticModule(sym) && (sym.companionClass == NoSymbol)
      }

      private[this] def isTopLevelModule(sym: Symbol): Boolean = {
        atPhase (currentRun.picklerPhase.next) {
          sym.isModuleClass && !sym.isImplClass && !sym.isNestedClass
        }
      }

      private[this] def isStaticModule(sym: Symbol): Boolean = {
        sym.isModuleClass && !sym.isImplClass && !sym.hasFlag(Flags.LIFTED)
      }
    }
  }
}
