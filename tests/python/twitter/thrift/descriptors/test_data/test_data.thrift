// This test thrift IDL is taken from http://wiki.apache.org/thrift/Tutorial, with
// the original comments stripped out, and some extra code added.

include "shared.thrift"

namespace cpp tutorial
namespace java tutorial
php_namespace tutorial
namespace perl tutorial
smalltalk_category Thrift-Tutorial
namespace * tutorial.for.all
xsd_namespace "tutorial"

typedef i32 MyInteger

const i32 (basictypeannotation="foo") INT32CONSTANT = 9853
const map cpp_type "MyMap" <string,string> (containertypeannotation="foo") MAPCONSTANT = {'hello':'world', 'goodnight':'moon'}

enum Operation {
  ADD,
  SUBTRACT = 2 (enumvalueannotation="foo"),
  MULTIPLY = 3,
  DIVIDE = 0xa
} (enumannotation="foo")

// A comment

struct Work {
  1: i32 num1 = 0,
  2: required i64 num2,
  3: Operation op,
  4: optional string comment,
  5: optional i32 num3 = INT32CONSTANT
} (foo="foo" bar="bar")

union MoreWork {
  1: optional map<double (foo="bar"), list<map<i32, set cpp_type "MySet" <Work>>> cpp_type "MyList"> crazyWorkList,
  2: optional bool flag,
  3: optional i16 short;  // Try out a semicolon (and an inline comment...)
  4: optional byte tiny,  # Another inline comment
  5: optional binary blob /* And yet another inline comment */
} (foo="foo", bar="bar")

exception InvalidOperation {
  /* A multiline
     block
     comment. */
  1: i32 what (fieldannotation="foo"),
  2: string why
} (exceptionannotation="foo")

service Calculator extends shared.SharedService {

   void ping() (functionannotation="bar"),

   i32 add(1:i32 num1 /* an embedded comment */, 2:i32 num2),

   i32 calculate(1:i32 logid, 2:Work w) throws (1:InvalidOperation ouch),

   oneway void zip(),
} (serviceannotation="foo")

service CalculatorExtreme extends shared.SharedService {
    void pingExtreme(),
}

// We don't store senums or xsd information in the descriptors, but we want to make sure
// we can parse and ignore them.

senum Foo {
  "FOO", "BAR", "BAZ"
}

struct XsdTestStruct xsd_all {
  1: string foo = "foo" xsd_optional xsd_nillable xsd_attributes { 1: i32 bar, 2: bool baz }
}



