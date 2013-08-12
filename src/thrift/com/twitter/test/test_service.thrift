// A few, mostly simple thrift structs for FormRendererTest.

namespace py twitter_test.service_thrift
namespace java com.twitter.test.test_service

struct Foo {
  1: i64 i1
  2: string i2
}

struct Bar {
  1: string i3
}

struct Baz {
  1: string i3;
  // This field demonstrates a complex data type with nested generics.
  // It's named in honor of rschonberger who made me notice that FormRenderer does not support this
  // type of data.
  2: map<string, list<Foo> > rschonberger;
}

service FooService {
  Bar foobar(1: Foo foo);
  Baz foobaz(1: Foo foo, 2: Bar bar);
}
