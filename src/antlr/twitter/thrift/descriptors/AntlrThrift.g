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

// Generates a parser that parses thrift IDL files and emits a simplified Abstract Syntax Tree.
// ThriftTreeWalker.g then parses that AST and generates descriptors.
//
// For completeness, this grammar accepts the entire thrift IDL language, even the rarely used
// parts that we don't support. The tree walker acts only on the subset it supports and ignores
// the rest.

// Author: David Helder

grammar AntlrThrift;

options {
  language = Python;
  output = AST;
}

tokens {
  COCOA;
  CONST;
  CONST_LIST;
  CONST_MAP;
  CPP;
  CPP_INCLUDE;
  CPP_TYPE;
  CSHARP;
  DEFAULT;
  ENUM;
  ENUM_DEF;
  EXCEPTION;
  EXTENDS;
  FIELD;
  FUNCTION;
  INCLUDE;
  JAVA;
  LIST;
  MAP;
  NAMESPACE;
  ONEWAY;
  PAIR;
  PERL;
  PHP;
  PROGRAM;
  PY;
  REQUIRED;
  SENUM;
  STRING;
  BINARY;
  SLIST;
  BOOL;
  BYTE;
  I16;
  I32;
  I64;
  DOUBLE;
  VOID;
  OPTIONAL;
  RB;
  SERVICE;
  SET;
  SMALLTALK_CATEGORY;
  SMALLTALK_PREFIX;
  STAR;
  STRUCT;
  THROWS;
  TYPEDEF;
  TYPE_ANNOTATION;
  UNION;
  XSD;
  XSD_ALL;
  XSD_ATTRIBUTES;
  XSD_NILLABLE;
  XSD_OPTIONAL;
}

// Try and turn off recovery as much as possible, so we can fail fast.
// Unfortunately this doesn't seem to work well. In many cases the parser
// will fail silently and return a partial program.
// TODO: Fix this.

@rulecatch {
  except RecognitionException as e:
      raise e
}

@members {
  def mismatch(self, input, ttype, follow):
    raise MismatchedTokenException(ttype, input)

  def recoverFromMismatchedToken(self, input, ttype, follow):
    raise RecognitionException(ttype, input)

  def recoverFromMismatchedToken(self, input, ttype, follow):
    raise RecognitionException(ttype, input)

  def recoverFromMismatchedSet(self, input, re, follow):
    raise re
}

@lexer::members {
  def recover(self, e):
    raise e
}

/* ******************** */

program:
  header* definition* -> ^(PROGRAM header* definition*);

header:
  'include'                LITERAL       -> ^(INCLUDE LITERAL) |
  'cpp_include'            LITERAL       -> ^(CPP_INCLUDE LITERAL)  |
  'namespace' t=IDENTIFIER n=IDENTIFIER  -> ^(NAMESPACE $t $n) |
  'namespace' '*'          IDENTIFIER    -> ^(NAMESPACE STAR IDENTIFIER) |
  'cpp_namespace'          IDENTIFIER    -> ^(NAMESPACE IDENTIFIER['cpp'] IDENTIFIER) |
  'php_namespace'          IDENTIFIER    -> ^(NAMESPACE IDENTIFIER['php'] IDENTIFIER) |
  'py_module'              IDENTIFIER    -> ^(NAMESPACE IDENTIFIER['py'] IDENTIFIER) |
  'perl_package'           IDENTIFIER    -> ^(NAMESPACE IDENTIFIER['perl'] IDENTIFIER) |
  'ruby_namespace'         IDENTIFIER    -> ^(NAMESPACE IDENTIFIER['rb'] IDENTIFIER) |
  'smalltalk_category'     ST_IDENTIFIER -> ^(NAMESPACE IDENTIFIER['smalltalk.category'] ST_IDENTIFIER) |
  'smalltalk_prefix'       IDENTIFIER    -> ^(NAMESPACE IDENTIFIER['smalltalk.prefix'] IDENTIFIER) |
  'java_package'           IDENTIFIER    -> ^(NAMESPACE IDENTIFIER['java'] IDENTIFIER) |
  'cocoa_package'          IDENTIFIER    -> ^(NAMESPACE IDENTIFIER['cocoa'] IDENTIFIER) |
  'xsd_namespace'          LITERAL       -> ^(NAMESPACE IDENTIFIER['xsd'] LITERAL) |
  'csharp_namespace'       IDENTIFIER    -> ^(NAMESPACE IDENTIFIER['csharp'] IDENTIFIER);

definition:
  const |
  typeDefinition |
  service;

typeDefinition:
  typedef |
  enum |
  senum |
  struct |
  union |
  xception;

typedef:
  'typedef' fieldType IDENTIFIER typeAnnotations? -> ^(TYPEDEF fieldType IDENTIFIER typeAnnotations?);

commaOrSemicolon:
  (',' | ';');

enum:
  'enum' IDENTIFIER '{' enumDef* '}' typeAnnotations? -> ^(ENUM IDENTIFIER enumDef* typeAnnotations?);

enumDef:
  IDENTIFIER '=' intConstant typeAnnotations? commaOrSemicolon? -> ^(ENUM_DEF IDENTIFIER intConstant typeAnnotations?) |
  IDENTIFIER typeAnnotations? commaOrSemicolon?                 -> ^(ENUM_DEF IDENTIFIER typeAnnotations?);

senum:
  'senum' IDENTIFIER '{' senumDef* '}' typeAnnotations? -> ;

senumDef:
  LITERAL commaOrSemicolon?;

const:
  'const' ft=fieldType id=IDENTIFIER '=' cv=constValue commaOrSemicolon?
    -> ^(CONST $ft $id $cv);

constValue:
  intConstant |
  DUBCONSTANT |
  LITERAL |
  IDENTIFIER |
  constList |
  constMap;

constList:
  '[' (constValue commaOrSemicolon?)* ']' -> ^(CONST_LIST constValue*);

constMap:
  '{' (constValuePair)* '}' -> ^(CONST_MAP constValuePair*);

constValuePair:
  k=constValue ':' v=constValue commaOrSemicolon? -> ^(PAIR $k $v);

struct:
  'struct' IDENTIFIER xsdAll? '{' field* '}' typeAnnotations?
    -> ^(STRUCT IDENTIFIER xsdAll? field* typeAnnotations?);

union:
  'union' IDENTIFIER xsdAll? '{' field* '}' typeAnnotations?
    -> ^(UNION IDENTIFIER xsdAll? field* typeAnnotations?);

xsdAll:
  'xsd_all' -> XSD_ALL;

xsdOptional:
  'xsd_optional' -> XSD_OPTIONAL;

xsdNillable:
  'xsd_nillable' -> XSD_NILLABLE;

xsdAttributes:
  'xsd_attributes' '{' field* '}' -> ^(XSD_ATTRIBUTES field*);

xception:
  'exception' IDENTIFIER '{' field* '}' typeAnnotations? -> ^(EXCEPTION IDENTIFIER field* typeAnnotations?);

service:
  'service' IDENTIFIER extends? '{' function* '}' typeAnnotations? -> ^(SERVICE IDENTIFIER extends? function* typeAnnotations?);

extends:
  'extends' IDENTIFIER -> ^(EXTENDS IDENTIFIER);

function:
  oneway? functionType IDENTIFIER '(' field* ')' throwz? typeAnnotations? commaOrSemicolon?
  -> ^(FUNCTION oneway? functionType IDENTIFIER field* throwz? typeAnnotations?);

oneway:
  'oneway' -> ONEWAY;

throwz:
  'throws' '(' field* ')' -> ^(THROWS field*);

field:
  fieldIdentifier? fieldRequiredness? fieldType IDENTIFIER fieldValue?
    xsdOptional? xsdNillable? xsdAttributes? typeAnnotations?
    commaOrSemicolon?
   -> ^(FIELD fieldIdentifier? fieldRequiredness? fieldType IDENTIFIER fieldValue?
        xsdOptional? xsdNillable? xsdAttributes? typeAnnotations?);

fieldIdentifier:
  intConstant ':' -> intConstant;

fieldRequiredness:
  'required' -> REQUIRED |
  'optional' -> OPTIONAL;

fieldValue:
  '=' constValue -> DEFAULT constValue;

functionType:
  'void' -> VOID |
  fieldType;

fieldType:
  IDENTIFIER |
  baseType |
  containerType;

baseType:
  simpleBaseType typeAnnotations?;

simpleBaseType:
  'string' -> STRING |
  'binary' -> BINARY |
  'slist'  -> SLIST  |
  'bool'   -> BOOL   |
  'byte'   -> BYTE   |
  'i16'    -> I16    |
  'i32'    -> I32    |
  'i64'    -> I64    |
  'double' -> DOUBLE;

containerType:
  simpleContainerType typeAnnotations?;

simpleContainerType:
  mapType | setType | listType;

mapType:
  'map' cppType? '<' ft1=fieldType ',' ft2=fieldType '>' -> ^(MAP cppType? $ft1 $ft2);

setType:
  'set' cppType? '<' ft=fieldType '>' -> ^(SET cppType? $ft);

// It's weird, and probably an error, but the original thrift yacc
// grammar puts cppType after the angle brackets for lists, but before
// for sets and maps. The cpp_type isn't actually used anyway, so this
// probably doesn't actually matter.
listType:
  'list' '<' ft=fieldType '>' cppType? -> ^(LIST cppType? $ft);

cppType:
  'cpp_type' i=LITERAL -> ^(CPP_TYPE $i);

typeAnnotations:
  '(' typeAnnotation* ')' -> typeAnnotation*;

typeAnnotation:
  i=IDENTIFIER '=' l=LITERAL commaOrSemicolon? -> ^(TYPE_ANNOTATION $i $l);

intConstant: INTCONSTANT | HEXCONSTANT;

/* ******************** */

INTCONSTANT   : ('+' | '-')? '0'..'9'+;
HEXCONSTANT   : '0x' ('0'..'9' | 'a'..'f' | 'A'..'F')+;
DUBCONSTANT   : ('+' | '-')? '0'..'9'*
                ('.' '0'..'9'+)?
                (('e' | 'E') ('+' | '-')? '0'..'9'+)?;
IDENTIFIER    : ('a'..'z' | 'A'..'Z' | '_')
                ('.' | 'a'..'z' | 'A'..'Z' | '_' | '0'..'9')*;
WHITESPACE    : (' ' | '\t' | '\r' | '\n')* {$channel=HIDDEN;};
MULTICOMM     : '/*' ( options {greedy=false;} : . )* '*/' {$channel=HIDDEN;};
COMMENT       : ('//' (~'\n')*) {$channel=HIDDEN;};
UNIXCOMMENT   : ('#' (~'\n')*) {$channel=HIDDEN;};
ST_IDENTIFIER : ('a'..'z' | 'A'..'Z' | '-')
                ('.' | 'a'..'z' | 'A'..'Z' | '_' | '0'..'9' | '-')*;
LITERAL       : (('\'' (~'\'')* '\'') | ('"' (~'"')* '"'));
