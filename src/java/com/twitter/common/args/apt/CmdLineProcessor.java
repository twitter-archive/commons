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

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitor6;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import com.twitter.common.args.Arg;
import com.twitter.common.args.ArgParser;
import com.twitter.common.args.CmdLine;
import com.twitter.common.args.Parser;
import com.twitter.common.args.Positional;
import com.twitter.common.args.Verifier;
import com.twitter.common.args.VerifierFor;
import com.twitter.common.args.apt.Configuration.ParserInfo;

import static com.twitter.common.args.apt.Configuration.ArgInfo;

/**
 * Processes {@literal @CmdLine} annotated fields and {@literal @ArgParser} and
 * {@literal @VerifierFor} parser and verifier registrations and stores configuration data listing
 * these fields, parsers and verifiers on the classpath for discovery via
 * {@link com.twitter.common.args.apt.Configuration#load()}.
 */
@SupportedOptions({
    CmdLineProcessor.MAIN_OPTION,
    CmdLineProcessor.CHECK_LINKAGE_OPTION
})
public class CmdLineProcessor extends AbstractProcessor {
  static final String MAIN_OPTION =
      "com.twitter.common.args.apt.CmdLineProcessor.main";
  static final String CHECK_LINKAGE_OPTION =
      "com.twitter.common.args.apt.CmdLineProcessor.check_linkage";

  private static final Function<Class<?>, String> GET_NAME = new Function<Class<?>, String>() {
    @Override public String apply(Class<?> type) {
      return type.getName();
    }
  };

  /** Existing merged Configuration loaded from the classpath. */
  private Supplier<Configuration> persistedConfig =
    Suppliers.memoize(new Supplier<Configuration>() {
      @Override public Configuration get() {
        try {
          return Configuration.load();
        } catch (IOException e) {
          throw new RuntimeException("Failed to load existing Arg fields:", e);
        }
      }
    });
  /** New configurations for this round. */
  private Map<String, Configuration.Builder> roundConfigs =
    new HashMap<String, Configuration.Builder>();
  /** Relevant classnames that were alive in any round. */
  private final ImmutableSet.Builder<String> liveClassNamesBuilder = ImmutableSet.builder();

  private Types typeUtils;
  private Elements elementUtils;
  private boolean isMain;
  private boolean isCheckLinkage;

  private static boolean getBooleanOption(Map<String, String> options, String name,
      boolean defaultValue) {

    if (!options.containsKey(name)) {
      return defaultValue;
    }

    // We want to map the presence of a boolean option without a value to indicate true, giving the
    // following accepted boolean option formats:
    // -Afoo -> true
    // -Afoo=false -> false
    // -Afoo=true -> true

    String isOption = options.get(name);
    return (isOption == null) || Boolean.parseBoolean(isOption);
  }

  @Override
  public void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);

    typeUtils = processingEnv.getTypeUtils();
    elementUtils = processingEnv.getElementUtils();

    Map<String, String> options = processingEnv.getOptions();
    isMain = getBooleanOption(options, MAIN_OPTION, false);
    isCheckLinkage = getBooleanOption(options, CHECK_LINKAGE_OPTION, true);
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latest();
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.copyOf(Iterables.transform(
        ImmutableList.of(Positional.class, CmdLine.class, ArgParser.class, VerifierFor.class),
        GET_NAME));
  }

  /** Get or create a Configuration.Builder for the current round. */
  private Configuration.Builder getBuilder(String className) {
    Configuration.Builder builder = this.roundConfigs.get(className);
    if (builder == null) {
      builder = new Configuration.Builder(className);
      this.roundConfigs.put(className, builder);
    }
    return builder;
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    try {
      // Collect all live classnames from this round, so that if they lose all annotated fields, we
      // can delete their previous resources.
      liveClassNamesBuilder.addAll(extractClassNames(roundEnv));

      // Then collect all relevant annotated classes.
      Set<? extends Element> parsers = getAnnotatedElements(roundEnv, ArgParser.class);
      @Nullable Set<String> parsedTypes = getParsedTypes(parsers);

      Set<? extends Element> cmdlineArgs = getAnnotatedElements(roundEnv, CmdLine.class);
      Set<? extends Element> positionalArgs = getAnnotatedElements(roundEnv, Positional.class);

      ImmutableSet<? extends Element> invalidArgs =
          Sets.intersection(cmdlineArgs, positionalArgs).immutableCopy();
      if (!invalidArgs.isEmpty()) {
        error("An Arg cannot be annotated with both @CmdLine and @Positional, found bad Arg "
            + "fields: %s", invalidArgs);
      }

      for (ArgInfo cmdLineInfo : processAnnotatedArgs(parsedTypes, cmdlineArgs, CmdLine.class)) {
        getBuilder(cmdLineInfo.className).addCmdLineArg(cmdLineInfo);
      }

      for (ArgInfo positionalInfo
          : processAnnotatedArgs(parsedTypes, positionalArgs, Positional.class)) {
        getBuilder(positionalInfo.className).addPositionalInfo(positionalInfo);
      }
      checkPositionalArgsAreLists(roundEnv);

      processParsers(parsers);

      Set<? extends Element> verifiers = getAnnotatedElements(roundEnv, VerifierFor.class);
      processVerifiers(verifiers);

      if (roundEnv.processingOver()) {
        for (String className : this.liveClassNamesBuilder.build()) {
          FileObject cmdLinePropertiesResource = createCommandLineDb(className);
          Configuration.Builder configBuilder = getBuilder(className);
          // Delete the config resource for classes which no longer exist, or which have
          // no fields.
          if (configBuilder.isEmpty()) {
            cmdLinePropertiesResource.delete();
            continue;
          }
          // Otherwise, write a new copy of the resource.
          Writer writer = null;
          try {
            writer = cmdLinePropertiesResource.openWriter();
            configBuilder.build().store(writer, "Generated via apt by " + getClass().getName());
          } catch (IOException e) {
            throw new RuntimeException(
                "Failed to write Arg resource file for " + className + ":",
                e);
          } finally {
            closeQuietly(writer);
          }
        }
      }
    } catch (RuntimeException e) {      // SUPPRESS CHECKSTYLE IllegalCatch
      // Catch internal errors - when these bubble more useful queued error messages are lost in
      // some javac implementations.
      error("Unexpected error completing annotation processing:\n%s",
          Throwables.getStackTraceAsString(e));
    }
    return true;
  }

  /** Return classNames from the given RoundEnvironment. */
  private Iterable<String> extractClassNames(RoundEnvironment roundEnv) {
    ImmutableList.Builder<String> classNames = new ImmutableList.Builder<String>();
    for (Element element : roundEnv.getRootElements()) {
      classNames.addAll(extractClassNames(element));
    }
    return classNames.build();
  }

  /** If the given element is a class, returns its className, and those of its inner classes. */
  private Iterable<String> extractClassNames(Element element) {
    if (!(element instanceof TypeElement)) {
      return ImmutableList.<String>of();
    }
    Iterable<String> classNames = ImmutableList.of(getBinaryName((TypeElement) element));
    for (Element child : element.getEnclosedElements()) {
      classNames = Iterables.concat(classNames, extractClassNames(child));
    }
    return classNames;
  }

  private void closeQuietly(Closeable closeable) {
    if (closeable == null) {
      return;
    }
    try {
      closeable.close();
    } catch (IOException e) {
      log(Kind.MANDATORY_WARNING, "Failed to close %s: %s", closeable, e);
    }
  }

  private void checkPositionalArgsAreLists(RoundEnvironment roundEnv) {
    for (Element positionalArg : getAnnotatedElements(roundEnv, Positional.class)) {
      @Nullable TypeMirror typeArgument =
          getTypeArgument(positionalArg.asType(), typeElement(Arg.class));
      if ((typeArgument == null)
          || !typeUtils.isSubtype(typeElement(List.class).asType(), typeArgument)) {
        error("Found @Positional %s %s.%s that is not a List",
            positionalArg.asType(), positionalArg.getEnclosingElement(), positionalArg);
      }
    }
  }

  @Nullable
  private Set<String> getParsedTypes(Set<? extends Element> parsers) {
    if (!isCheckLinkage) {
      return null;
    }

    Iterable<String> parsersFor = Optional.presentInstances(Iterables.transform(parsers,
        new Function<Element, Optional<String>>() {
          @Override public Optional<String> apply(Element parser) {
            TypeMirror parsedType = getTypeArgument(parser.asType(), typeElement(Parser.class));
            if (parsedType == null) {
              error("failed to find a type argument for Parser: %s", parser);
              return Optional.absent();
            }
            // Equals on TypeMirrors doesn't work - so we compare string representations :/
            return Optional.of(typeUtils.erasure(parsedType).toString());
          }
        }));
    parsersFor = Iterables.concat(parsersFor, Iterables.filter(
        Iterables.transform(this.persistedConfig.get().parserInfo(),
            new Function<ParserInfo, String>() {
              @Override @Nullable public String apply(ParserInfo parserInfo) {
                TypeElement typeElement = elementUtils.getTypeElement(parserInfo.parsedType);
                // We may not have a type on the classpath for a previous round - this is fine as
                // long as the no Args in this round that are of the type.
                return (typeElement == null)
                    ? null : typeUtils.erasure(typeElement.asType()).toString();
              }
            }), Predicates.notNull()));
    return ImmutableSet.copyOf(parsersFor);
  }

  private Iterable<ArgInfo> processAnnotatedArgs(
      @Nullable final Set<String> parsedTypes,
      Set<? extends Element> args,
      final Class<? extends Annotation> argAnnotation) {

    return Optional.presentInstances(Iterables.transform(args,
        new Function<Element, Optional<ArgInfo>>() {
          @Override public Optional<ArgInfo> apply(Element arg) {
            @Nullable TypeElement containingType = processArg(parsedTypes, arg, argAnnotation);
              if (containingType == null) {
                return Optional.absent();
              } else {
                return Optional.of(new ArgInfo(getBinaryName(containingType),
                    arg.getSimpleName().toString()));
              }
            }
        }));
  }

  private Set<? extends Element> getAnnotatedElements(RoundEnvironment roundEnv,
      Class<? extends Annotation> argAnnotation) {
    return roundEnv.getElementsAnnotatedWith(typeElement(argAnnotation));
  }

  @Nullable
  private TypeElement processArg(@Nullable Set<String> parsedTypes, Element annotationElement,
      Class<? extends Annotation> annotationType) {

    TypeElement parserType = typeElement(Parser.class);
    if (annotationElement.getKind() != ElementKind.FIELD) {
      error("Found a @%s annotation on a non-field %s",
          annotationType.getSimpleName(), annotationElement);
      return null;
    } else {
      // Only types contain fields so this cast is safe.
      TypeElement containingType = (TypeElement) annotationElement.getEnclosingElement();

      if (!isAssignable(annotationElement.asType(), Arg.class)) {
        error("Found a @%s annotation on a non-Arg %s.%s",
            annotationType.getSimpleName(), containingType, annotationElement);
        return null;
      }
      if (!annotationElement.getModifiers().contains(Modifier.STATIC)) {
        return null;
      }

      if (parsedTypes != null) {
        // Check Parser<T> linkage for the Arg<T> type T.
        TypeMirror typeArgument =
            getTypeArgument(annotationElement.asType(), typeElement(Arg.class));
        @Nullable AnnotationMirror cmdLine =
            getAnnotationMirror(annotationElement, typeElement(annotationType));
        if (cmdLine != null) {
          TypeMirror customParserType = getClassType(cmdLine, "parser", parserType).asType();
          if (typeUtils.isSameType(parserType.asType(), customParserType)) {
            if (!checkTypePresent(parsedTypes, typeArgument)) {
              error("No parser registered for %s; %s.%s is un-parseable",
                  typeArgument, containingType, annotationElement);
            }
          } else {
            TypeMirror customParsedType = getTypeArgument(customParserType, parserType);
            if (!isAssignable(typeArgument, customParsedType)) {
              error("Custom parser %s parses %s but registered for %s.%s with Arg type %s",
                  customParserType, customParsedType, containingType, annotationElement,
                  typeArgument);
            }
          }
        }
      }

      // TODO(John Sirois): Add additional compile-time @CmdLine verification for:
      // 1.) for each @CmdLine Arg<T> annotated with @VerifierFor.annotation: T is a subtype of
      //     V where there is a Verifier<V>
      // 2.) name checks, including dups

      return containingType;
    }
  }

  private boolean checkTypePresent(Set<String> types, TypeMirror type) {
    Iterable<TypeMirror> allTypes = getAllTypes(type);
    for (TypeMirror t : allTypes) {
      if (types.contains(typeUtils.erasure(t).toString())) {
        return true;
      }
    }
    return false;
  }

  private void processParsers(Set<? extends Element> elements) {
    TypeElement parserType = typeElement(Parser.class);
    for (Element element : elements) {
      if (element.getKind() != ElementKind.CLASS) {
        error("Found an @ArgParser annotation on a non-class %s", element);
      } else {
        TypeElement parser = (TypeElement) element;
        if (!isAssignable(parser, Parser.class)) {
          error("Found an @ArgParser annotation on a non-Parser %s", element);
          return;
        }

        @Nullable String parsedType = getTypeArgument(parser, parserType);
        if (parsedType != null) {
          String parserClassName = getBinaryName(parser);
          getBuilder(parserClassName).addParser(parsedType, getBinaryName(parser));
        }
      }
    }
  }

  private void processVerifiers(Set<? extends Element> elements) {
    TypeElement verifierType = typeElement(Verifier.class);
    TypeElement verifierForType = typeElement(VerifierFor.class);
    for (Element element : elements) {
      if (element.getKind() != ElementKind.CLASS) {
        error("Found a @VerifierFor annotation on a non-class %s", element);
      } else {
        TypeElement verifier = (TypeElement) element;
        if (!isAssignable(verifier, Verifier.class)) {
          error("Found a @Verifier annotation on a non-Verifier %s", element);
          return;
        }
        String verifierClassName = getBinaryName(verifier);

        @Nullable AnnotationMirror verifierFor = getAnnotationMirror(verifier, verifierForType);
        if (verifierFor != null) {
          @Nullable TypeElement verifyAnnotationType = getClassType(verifierFor, "value", null);
          if (verifyAnnotationType != null) {
            @Nullable String verifiedType = getTypeArgument(verifier, verifierType);
            if (verifiedType != null) {
              String verifyAnnotationClassName =
                  elementUtils.getBinaryName(verifyAnnotationType).toString();
              getBuilder(verifierClassName).addVerifier(verifiedType, verifyAnnotationClassName,
                  verifierClassName);
            }
          }
        }
      }
    }
  }

  @Nullable
  private String getTypeArgument(TypeElement annotatedType, final TypeElement baseType) {
    TypeMirror typeArgument = getTypeArgument(annotatedType.asType(), baseType);
    return typeArgument == null
        ? null
        : getBinaryName((TypeElement) typeUtils.asElement(typeArgument));
  }

  private Iterable<TypeMirror> getAllTypes(TypeMirror type) {
    return getAllTypes(new HashSet<String>(), Lists.<TypeMirror>newArrayList(), type);
  }

  private Iterable<TypeMirror> getAllTypes(Set<String> visitedTypes, List<TypeMirror> types,
      TypeMirror type) {

    String typeName = typeUtils.erasure(type).toString();
    if (!visitedTypes.contains(typeName)) {
      types.add(type);
      visitedTypes.add(typeName);
      for (TypeMirror superType : typeUtils.directSupertypes(type)) {
        getAllTypes(visitedTypes, types, superType);
      }
    }
    return types;
  }

  @Nullable
  private TypeMirror getTypeArgument(TypeMirror annotatedType, final TypeElement baseType) {
    for (TypeMirror type : getAllTypes(annotatedType)) {
      TypeMirror typeArgument = type.accept(new SimpleTypeVisitor6<TypeMirror, Void>() {
        @Override public TypeMirror visitDeclared(DeclaredType t, Void aVoid) {
          if (isAssignable(t, baseType)) {
            List<? extends TypeMirror> typeArguments = t.getTypeArguments();
            if (!typeArguments.isEmpty()) {
              return typeUtils.erasure(typeArguments.get(0));
            }
          }
          return null;
        }
      }, null);

      if (typeArgument != null) {
        return typeArgument;
      }
    }
    error("Failed to find a type argument for %s in %s", baseType, annotatedType);
    return null;
  }

  @Nullable
  private AnnotationMirror getAnnotationMirror(Element element, TypeElement annotationType) {
    for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
      if (typeUtils.isSameType(annotationMirror.getAnnotationType(),  annotationType.asType())) {
        return annotationMirror;
      }
    }
    error("Failed to find an annotation of type %s on %s", annotationType, element);
    return null;
  }

  @SuppressWarnings("unchecked")
  private TypeElement getClassType(AnnotationMirror annotationMirror, String methodName,
      TypeElement defaultClassType) {

    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
        : annotationMirror.getElementValues().entrySet()) {
      if (entry.getKey().getSimpleName().equals(elementUtils.getName(methodName))) {
        TypeElement classType = entry.getValue().accept(
            new SimpleAnnotationValueVisitor6<TypeElement, Void>() {
              @Override public TypeElement visitType(TypeMirror t, Void unused) {
                return (TypeElement) processingEnv.getTypeUtils().asElement(t);
              }
            }, null);

        if (classType != null) {
          return classType;
        }
      }
    }
    if (defaultClassType == null) {
      error("Could not find a class type for %s.%s", annotationMirror, methodName);
    }
    return defaultClassType;
  }

  @Nullable
  private FileObject createCommandLineDb(String className) {
    // TODO: Move into the Configuration package somehow.
    return createResource(
        Configuration.DEFAULT_RESOURCE_PACKAGE,
        className + Configuration.DEFAULT_RESOURCE_SUFFIX);
  }

  @Nullable
  private FileObject createResource(String packageName, String name) {
    try {
      return processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT,
          packageName, name);
    } catch (IOException e) {
      throw new RuntimeException(
          "Failed to create resource file to store %s/%s:".format(packageName, name), e);
    }
  }

  private TypeElement typeElement(Class<?> type) {
    return elementUtils.getTypeElement(type.getName());
  }

  private String getBinaryName(TypeElement typeElement) {
    return elementUtils.getBinaryName(typeElement).toString();
  }

  private boolean isAssignable(TypeElement subType, Class<?> baseType) {
    return isAssignable(subType.asType(), baseType);
  }

  private boolean isAssignable(TypeMirror subType, Class<?> baseType) {
    return isAssignable(subType, typeElement(baseType));
  }

  private boolean isAssignable(TypeMirror subType, TypeElement baseType) {
    return isAssignable(subType, baseType.asType());
  }

  private boolean isAssignable(TypeMirror subType, TypeMirror baseType) {
    return typeUtils.isAssignable(typeUtils.erasure(subType), typeUtils.erasure(baseType));
  }

  private void error(String message, Object ... args) {
    log(Kind.ERROR, message, args);
  }

  private void log(Kind kind, String message, Object ... args) {
    processingEnv.getMessager().printMessage(kind, String.format(message, args));
  }
}
