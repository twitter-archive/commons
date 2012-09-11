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

namespace java com.twitter.thrift.descriptors
namespace py gen.twitter.thrift.descriptors
namespace rb Thriftutils.Descriptors

// Descriptors for all the various entities defined in a thrift file: types, services etc.
// These can be populated by a thrift file parser and then passed as template arguments
// to code generators.
//
// The descriptors are pretty self-explanatory, and so have minimal comments. The only tricky
// part is how recursive types are represented, which is explained in detail below.


/* Headers. */

struct Include {
  1: required string path
}

struct Namespace {
  1: required string language,
  2: required string name
}

struct Annotation {
  1: required string key,
  2: required string value
}

/* Types. */

enum SimpleBaseType {
  BOOL = 0,
  BYTE = 1,
  I16 = 2,
  I32 = 3,
  I64 = 4,
  DOUBLE = 5,
  STRING = 6,
  BINARY = 7
}

struct BaseType {
  1: required SimpleBaseType simpleBaseType,
  99: optional list<Annotation> annotations = []
}

// Thrift struct definitions cannot be recursive, so container types cannot contain
// their element types directly. Instead they indirect via a type id that can be
// dereferenced from a TypeRegistry.
// Note that type ids are opaque, not durable, and can change from parse to parse.

struct ListType {
  1: required string elementTypeId
}

struct SetType {
  1: required string elementTypeId
}

struct MapType {
  1: required string keyTypeId,
  2: required string valueTypeId
}

// Exactly one of these must be present. In languages with support for 'union' (currently
// Java and Ruby) this will be enforced automatically. In other languages this is just a
// regular struct, and we enforce the union constraint in code.
// TODO: Add Python support for 'union' in the Thrift compiler.
union SimpleContainerType {
  1: optional ListType listType,
  2: optional SetType setType,
  3: optional MapType mapType
}

struct ContainerType {
  1: required SimpleContainerType simpleContainerType,
  99: optional list<Annotation> annotations = []
}

// A reference to a type by its name or typedef'd alias.
struct Typeref {
  1: required string typeAlias
}

// Exactly one of these must be present. In languages with support for 'union' (currently
// Java and Ruby) this will be enforced automatically. In other languages this is just a
// regular struct, and we enforce the union constraint in code.
// TODO: Add Python support for 'union' in the Thrift compiler.
union SimpleType {
  1: optional BaseType baseType,
  2: optional ContainerType containerType,
  3: optional Typeref typeref
}

struct Type {
  1: required string id,
  2: required SimpleType simpleType
}

struct Typedef {
  1: required string typeId,
  2: required string typeAlias,
  99: optional list<Annotation> annotations = []
}

// A registry of all the types referenced in a thrift program.
//
// Note that identical types are not unique: E.g., two different mentions of list<string>
// will get two different type ids. This is necessary, since types can be annotated.
struct TypeRegistry {
  // A map from id to type. Used to resolve type ids, e.g., in container types.
  // Note that type ids are opaque, not durable, and can change from parse to parse.
  1: map<string, Type> idToType,

  // A map from alias to type id. Aliases are created using typedef.
  2: map<string, string> aliasToTypeId
}


/* Constants. */

struct Const {
  1: required string typeId,
  2: required string name,
  3: required string value
}


/* Enumerations. */

struct EnumElement {
  1: required string name,
  2: required i32 value,
  99: optional list<Annotation> annotations = []
}

struct Enum {
  1: required string name,
  2: required list<EnumElement> elements,
  99: optional list<Annotation> annotations = []
}


/* Structs, unions and exceptions. */

enum Requiredness {
  REQUIRED = 0,
  OPTIONAL = 1
}

struct Field {
  1: required i16 identifier,
  2: required string name,
  3: required string typeId,
  4: optional Requiredness requiredness,
  5: optional string defaultValue,
  99: optional list<Annotation> annotations = []
}

struct Struct {
  1: required string name,
  2: required list<Field> fields,
  99: optional list<Annotation> annotations = []
}

struct Union {
  1: required string name,
  2: required list<Field> fields,
  99: optional list<Annotation> annotations = []
}

struct Exception {
  1: required string name,
  2: required list<Field> fields,
  99: optional list<Annotation> annotations = []
}


/* Services. */

struct Function {
  1: required string name,
  2: optional string returnTypeId,  // Unspecified means void.
  3: optional bool oneWay = 0,  // Thrift doesn't allow 'false'/'true' when specifying the default.
  4: required list<Field> argz,
  5: required list<Field> throwz,
  99: optional list<Annotation> annotations = []
}

struct Service {
  1: required string name,
  2: optional string extendz,
  3: required list<Function> functions,
  99: optional list<Annotation> annotations = []
}


// In the Thrift parsing code the collection of all elements in a .thrift file
// is referred to as a 'program'.
struct Program {
  1: optional list<Namespace> namespaces = [],
  2: optional list<Include> includes = [],
  3: optional list<Const> constants = [],
  4: optional list<Enum> enums = [],
  5: optional list<Typedef> typedefs = [],
  6: optional list<Struct> structs = [],
  7: optional list<Union> unions = [],
  8: optional list<Exception> exceptions = [],
  9: optional list<Service> services = [],

  // A registry of all types in the program. Used for resolving type references.
  98: required TypeRegistry typeRegistry
}
