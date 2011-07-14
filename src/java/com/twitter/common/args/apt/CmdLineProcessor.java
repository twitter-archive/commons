// =================================================================================================
// Copyright 2011 Twitter, Inc.
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

package com.twitter.common.args.apt;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.io.Closeables;

/**
 * Processes {@literal @CmdLine} annotated fields and stores configuration data listing these fields
 * on the classpath for discovery via {@link com.twitter.common.args.apt.Configuration#load()}.
 *
 * @author John Sirois
 */
@SupportedAnnotationTypes(CmdLineProcessor.CMD_LINE_ANNOTATION_CLASS_NAME)
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class CmdLineProcessor extends AbstractProcessor {
  static final String CMD_LINE_ANNOTATION_CLASS_NAME = "com.twitter.common.args.CmdLine";

  private final Configuration.Builder configBuilder =
      new Configuration.Builder();

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    TypeElement cmdLineAnnotation =
        processingEnv.getElementUtils().getTypeElement(CMD_LINE_ANNOTATION_CLASS_NAME);
    processCmdLineArgs(roundEnv.getElementsAnnotatedWith(cmdLineAnnotation));

    if (roundEnv.processingOver() && !configBuilder.isEmpty()) {
      Configuration configuration  = configBuilder.build();
      Writer cmdLinePropertiesResource = openCmdLinePropertiesResource();
      if (cmdLinePropertiesResource != null) {
        try {
          configuration.store(cmdLinePropertiesResource,
              "Generated via apt by " + getClass().getName());
        } finally {
          Closeables.closeQuietly(cmdLinePropertiesResource);
        }
      }
    }
    return true;
  }

  private void processCmdLineArgs(Set<? extends Element> elements) {
    Elements elementUtils = processingEnv.getElementUtils();
    for (Element element : elements) {
      if (element.getKind() != ElementKind.FIELD) {
        error("Found a @CmdLine annotation on a non-field %s", element);
      } else {
        // Only types contain fields so this cast is safe.
        TypeElement containingType = (TypeElement) element.getEnclosingElement();
        configBuilder.addCmdLineArg(elementUtils.getBinaryName(containingType).toString(),
            element.getSimpleName().toString());
      }
    }
  }

  private final Supplier<FileObject> commandLineDb =
      Suppliers.memoize(new Supplier<FileObject>() {
        @Nullable @Override public FileObject get() {
          try {
            return processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT,
                    Configuration.DEFAULT_RESOURCE_PACKAGE, Configuration.DEFAULT_RESOURCE_NAME);
          } catch (IOException e) {
            error("Failed to create resource file to store /%s/%s: %s",
                Configuration.DEFAULT_RESOURCE_PATH, Throwables.getStackTraceAsString(e));
            return null;
          }
        }
      });

  @Nullable
  private Writer openCmdLinePropertiesResource() {
    FileObject resource = commandLineDb.get();
    if (resource == null) {
      return null;
    }
    try {
      log(Kind.NOTE, "Writing %s", resource.toUri());
      return resource.openWriter();
    } catch (IOException e) {
      if (!resource.delete()) {
        log(Kind.WARNING, "Failed to clean up /%s/%s after a failing to open it for writing",
            Configuration.DEFAULT_RESOURCE_PATH);
      }
      error("Failed to open resource file to store /%s/%s: %s",
          Configuration.DEFAULT_RESOURCE_PATH, Throwables.getStackTraceAsString(e));
      return null;
    }
  }

  private void error(String message, Object ... args) {
    log(Kind.ERROR, message, args);
  }

  private void log(Kind kind, String message, Object ... args) {
    processingEnv.getMessager().printMessage(kind, String.format(message, args));
  }
}
