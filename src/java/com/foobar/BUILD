java_library(
  name = 'example_lib',
  dependencies = [
    '3rdparty:guava',
  ],
  sources = rglobs('*.java'),
  resources = ['example.txt'],
)

jvm_binary(
  name = 'example_bin',
  dependencies = [
    pants(':example_lib')
  ],
  main = 'com.foobar.Example'
)

