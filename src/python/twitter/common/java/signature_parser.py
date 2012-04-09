# ==================================================================================================
# Copyright 2011 Twitter, Inc.
# --------------------------------------------------------------------------------------------------
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this work except in compliance with the License.
# You may obtain a copy of the License in the LICENSE file, or at:
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ==================================================================================================

# TODO(wickman)  This is horribly broken in Python 3.x for the following reason:
#
# 2.x:
# >>> b'\n'[0:]
# '\n'
# >>> b'\n'[0]
# '\n'
#
# 3.x:
# >>> b'\n'[0:]
# b'\n'
# >>> b'\n'[0]
# 10
#
# Fix it!


_UNPARSED = (None, 0)

def _list_if_none(variable):
  if variable is not None:
    return variable
  else:
    return []

class ParseException(Exception):
  pass

class BaseType(object):
  """http://java.sun.com/docs/books/jvms/second_edition/html/ClassFile.doc.html#84645
  """

  _CHAR_MAP = {
    'B': "byte",       # _signed byte
    'C': "char",       # Unicode character
    'D': "double",     # double-precision floating-point value
    'F': "float",      # single-precision floating-point value
    'I': "int",        # integer
    'J': "long",       # long integer
# Handled by ClassSignature
#   'L': "reference",  # an instance of class <classname>
    'S': "short",      # signed short
    'Z': "boolean",    # true or false
# Handled by ArraySignature
#   '[': "reference",  # one array dimension
  }

  @staticmethod
  def match(data):
    if data[0] in BaseType._CHAR_MAP:
      return BaseType._CHAR_MAP[data[0]], 1
    else:
      #print '  BaseType(_UNPARSED @ %d, data = %s)' % (0, data)
      return _UNPARSED

# ------- signature parsing ---------

class ClassSignature(object):
  """
    ClassSignature:
      [FormalTypeParameters] SuperclassSignature SuperinterfaceSignature*
  """
  @staticmethod
  def match(data):
    offset = 0
    ftp, bytes_read = FormalTypeParameters.match(data[offset:])
    offset += bytes_read
    scs, bytes_read = ClassTypeSignature.match(data[offset:])
    if scs is None:
      return _UNPARSED
    offset += bytes_read
    super_sigs = []
    while offset < len(data):
      sis, bytes_read = ClassTypeSignature.match(data[offset:])
      if sis is None:
        break
      offset += bytes_read
      super_sigs.append(sis)
    return ClassSignature(ftp, scs, super_sigs), offset

  def __init__(self, ftp=None, scs=None, sis=None):
    self._formal_type_parameters = _list_if_none(ftp)
    self._superclass_signature = scs
    self._superinterface_signatures = _list_if_none(sis)

  def __str__(self):
    output = []
    if self._formal_type_parameters:
      output.append('<%s>' % ', '.join('%s' % s for s in self._formal_type_parameters))
    output.append('CLASS extends %s' % self._superclass_signature)
    if self._superinterface_signatures:
      output.append('implements %s' % ', '.join('%s' % s for s in self._superinterface_signatures))

    return 'ClassSignature(%s)' % ' '.join(output)

class ClassTypeSignature(object):
  """
    'L' PackageSpecifier* SimpleClassTypeSignature ClassTypeSignatureSuffix* ';'

    This grammar is totally incorrect
       "L" {Ident "/"} Ident OptTypeArguments {"." Ident OptTypeArguments} ";".
             ^ package specifier

  """
  @staticmethod
  def match(data):
    if data[0] != 'L':
      return _UNPARSED
    offset = 1

    package_class, bytes_read = PackageSpecifier.match(data[offset:])
    if package_class is None:
      return _UNPARSED
    offset += bytes_read

    package_arguments, bytes_read = TypeArguments.match(data[offset:])
    offset += bytes_read

    suffixes = []
    while data[offset] != ';':
      suffix, bytes_read = ClassTypeSignatureSuffix.match(data[offset:])
      if not suffix:
        return _UNPARSED
      suffixes.append(suffix)
      offset += bytes_read
    return ClassTypeSignature(package_class, package_arguments, suffixes), offset + 1

  def __init__(self, package=None, package_arguments=None, suffixes=None):
    self._package = package
    self._arguments = _list_if_none(package_arguments)
    self._suffixes = _list_if_none(suffixes)

  def __str__(self):
    output = ['%s' % self._package]
    if self._arguments:
      output.append('<')
      output.append(', '.join('%s' % s for s in self._arguments))
      output.append('>')
    if self._suffixes:
      output.append('suffixes')
      output.extend(self._suffixes)
    return '%s' % (''.join('%s' % s for s in output))

class Identifier(object):
  """
    Names of methods, fields and local variables are stored as unqualified
    names.  Unqualified names must not contain the characters '.', ';', '['
    or '/'.  Method names are further constrained so that, with the
    exception of the special method names (3.9) <init> and <clinit>, they
    must not contain the characters '<' or '>'.

    Possible RHS:
      ':'
      '/'

  """
  @staticmethod
  def match(data):
    _BAD_CHARACTERS = ('.', ';', '[', '/', '<', '>', # documented
                      ':')                           # inferred
    if data.startswith('<init>'):
      return Identifier('<init>'), len('<init>')
    elif data.startswith('<clinit>'):
      return Identifier('<clinit>'), len('<clinit>')
    offset = 0
    while data[offset] not in _BAD_CHARACTERS:
      offset += 1
    if offset > 0:
      return Identifier(data[0:offset]), offset
    else:
      return _UNPARSED

  def __init__(self, ident):
    self._identifier = ident

  def __str__(self):
    return '%s' % self._identifier

class ClassBound(object):
  """
    ':' [ FieldTypeSignature ]
  """
  @staticmethod
  def match(data):
    if data[0] != ':':
      return _UNPARSED
    offset = 1
    fieldsig, bytes_read = FieldTypeSignature.match(data[offset:])
    return ClassBound(fieldsig), offset + bytes_read

  def __init__(self, signature=None):
    self._signature = signature

  def __str__(self):
    # an empty signature means infer java.lang.Object.
    if self._signature is None:
      return 'java.lang.Object'
    else:
      return '%s' % self._signature

class InterfaceBound(object):
  """
    ':' FieldTypeSignature
  """
  @staticmethod
  def match(data):
    if data[0] != ':':
      return _UNPARSED
    offset = 1
    fieldsig, bytes_read = FieldTypeSignature.match(data[offset:])
    return InterfaceBound(fieldsig), offset + bytes_read

  def __init__(self, signature=None):
    self._signature = signature

  def __str__(self):
    return '%s' % self._signature

class FieldTypeSignature(object):
  """
    FieldTypeSignature:
      ClassTypeSignature
      ArrayTypeSignature
      TypeVariableSignature
  """
  @staticmethod
  def match(data):
    cls, offset = ClassTypeSignature.match(data)
    if cls is not None:
      return cls, offset

    array, offset = ArrayTypeSignature.match(data)
    if array is not None:
      return array, offset

    typev, offset = TypeVariableSignature.match(data)
    if typev is not None:
      return typev, offset

    return _UNPARSED

class PackageSpecifier(object):
  """
    Identifier '/' PackageSpecifier*
  """
  @staticmethod
  def match(data):
    package_sequence = []
    offset = 0
    while True:
      ident, bytes_read = Identifier.match(data[offset:])
      if ident is None:
        if len(package_sequence) == 0:
          return _UNPARSED
      else:
        offset += bytes_read
        package_sequence.append(ident)

      if data[offset] != '/':
        return PackageSpecifier(package_sequence), offset
      else:
        offset += 1

  def __init__(self, package_id):
    self._package = package_id

  def parent(self):
    return PackageSpecifier(self._package[0:-1])

  def leaf(self):
    return self._package[-1]

  def __str__(self):
    #return 'PackageSpecifier(%s)' % ('.'.join('%s' % foo._identifier for foo in self._package))
    return '.'.join('%s' % foo._identifier for foo in self._package)

class SimpleClassTypeSignature(object):
  """
    Identifier [ TypeArguments ]
  """
  @staticmethod
  def match(data):
    ident, offset = Identifier.match(data)
    if ident is None:
      return _UNPARSED
    type_args, bytes_read = TypeArguments.match(data[offset:])
    return SimpleClassTypeSignature(ident, type_args), offset + bytes_read

  def __init__(self, identifier, type_args=None):
    self._identifier = identifier
    self._type_arguments = _list_if_none(type_args)

  def __str__(self):
    appendix = ''
    if self._type_arguments:
      appendix = '<%s>' % ', '.join('%s' % s for s in self._type_arguments)
    return 'SimpleClassTypeSignature(%s%s)' % (
      self._identifier, appendix)

class ClassTypeSignatureSuffix(object):
  """
    '.' SimpleClassTypeSignature
  """
  @staticmethod
  def match(data):
    if data[0] != '.':
      return _UNPARSED
    scts, bytes_read = SimpleClassTypeSignature.match(data[1:])
    if scts is None:
      return _UNPARSED
    return ClassTypeSignatureSuffix(scts), bytes_read + 1

  def __init__(self, signature):
    self._signature = signature

  def __str__(self):
    return '.%s' % self._signature

class TypeVariableSignature(object):
  """
    'T' Identifier ';'
  """
  @staticmethod
  def match(data):
    if data[0] != 'T':
      return _UNPARSED
    offset = 1
    ident, bytes_read = Identifier.match(data[offset:])
    offset += bytes_read
    if data[offset] != ';':
      return _UNPARSED
    return TypeVariableSignature(ident), offset + 1

  def __init__(self, identifier):
    self._identifier = identifier

  def __str__(self):
    return '<%s>' % self._identifier

class TypeArguments(object):
  """
    '<' TypeArgument+ '>'
  """
  @staticmethod
  def match(data):
    if data[0] != '<':
      return _UNPARSED
    offset = 1

    type_args = []
    while data[offset] != '>':
      type_arg, bytes_read = TypeArgument.match(data[offset:])
      if type_arg is None:
        break
      type_args.append(type_arg)
      offset += bytes_read
    if len(type_args) == 0:
      return _UNPARSED
    return type_args, offset + 1

  def __init__(self, arguments):
    self._arguments = arguments


class TypeArgument(object):
  """
    [ WildcardIndicator ] FieldTypeSignature
    '*'
  """
  @staticmethod
  def match(data):
    if data[0] == '*':
      return TypeArgument(data[0], sig=None), 1

    offset = 0
    wildcard, bytes_read = WildcardIndicator.match(data[offset:])
    offset += bytes_read
    field_signature, bytes_read = FieldTypeSignature.match(data[offset:])
    if field_signature is None:
      return _UNPARSED
    return TypeArgument(wildcard, field_signature), offset + bytes_read

  def __init__(self, wildcard_indicator=None, sig=None):
    self._wildcard = wildcard_indicator
    self._signature = sig
    if self._wildcard == '*':
      assert self._signature is None

  def __str__(self):
    if self._wildcard == '*':
      return '?'
    elif self._wildcard:
      if self._wildcard == '+':
        return '? extends %s' % self._signature
      else:
        return '%s extends ?' % self._signature
    else:
      return '%s' % self._signature

class WildcardIndicator(object):
  """
    '+'
    '-'
  """
  @staticmethod
  def match(data):
    if data[0] == '+' or data[0] == '-':
      return data[0], 1
    return _UNPARSED

class ArrayTypeSignature(object):
  """
    '[' TypeSignature
  """
  @staticmethod
  def match(data):
    if data[0] != '[':
      return _UNPARSED
    offset = 1
    sig, bytes_read = TypeSignature.match(data[offset:])
    if sig is None:
      return _UNPARSED
    return ArrayTypeSignature(sig), offset + bytes_read

  def __init__(self, sig):
    self._signature = sig

  def __str__(self):
    return '[]%s' % self._signature

class VoidSignature(object):
  @staticmethod
  def match(data):
    if data[0] == 'V':
      return VoidSignature(), 1
    return _UNPARSED


  def __init__(self):
    pass

  def __str__(self):
    return 'void'

class TypeSignature(object):
  """
    TypeSignature:
       FieldTypeSignature
       BaseType
  """
  @staticmethod
  def match(data):
    offset = 0
    sig, bytes_read = FieldTypeSignature.match(data[offset:])
    if sig: return TypeSignature(sig), offset + bytes_read
    base, bytes_read = BaseType.match(data[offset:])
    if base: return TypeSignature(base), offset + bytes_read
    return _UNPARSED

  def __init__(self, sig):
    self._signature = sig

  def __str__(self):
    return '%s' % self._signature

class MethodTypeSignature(object):
  """
    [FormalTypeParameters] '(' TypeSignature* ')' ReturnType ThrowsSignature*

    For example:

    <T:Ljava/lang/Object;> ( Ljava/lang/Class<+TT;>; ) Lcom/twitter/common/base/Supplier<TT;>;
    ----------------------   -----------------------   ---------------------------------------
    FormalTypeParameters        TypeSignature*         ReturnType
  """
  @staticmethod
  def match(data):
    formal_type, offset = FormalTypeParameters.match(data)
    if data[offset] != '(':
      return _UNPARSED
    offset += 1
    type_sigs = []
    while True:
      type_sig, bytes_read = TypeSignature.match(data[offset:])
      if type_sig is not None:
        type_sigs.append(type_sig)
        offset += bytes_read
        continue
      else:
        if data[offset] != ')':
          return _UNPARSED
        else:
          break
    offset += 1
    return_type, bytes_read = ReturnType.match(data[offset:])
    if return_type is None:
      return _UNPARSED
    offset += bytes_read
    throws = []
    while offset < len(data):
      throw_sig, bytes_read = ThrowsSignature.match(data[offset:])
      if throw_sig is None:
        break
      throws.append(throw_sig)
      offset += bytes_read
    return MethodTypeSignature(formal_type, type_sigs, return_type, throws), offset

  def __init__(self, formal_type=None, type_sigs=None, return_type=None, throws=None):
    self._formal_type = _list_if_none(formal_type)
    self._type_signatures = _list_if_none(type_sigs)
    self._return_type = return_type
    self._throws = _list_if_none(throws)

  def __str__(self):
    output = ''
    if self._formal_type:
      output += ('<%s> ' % ', '.join('%s' % s for s in self._formal_type))
    output += ('%s' % self._return_type)
    output += (' METHOD')
    if self._type_signatures:
      output += ('(%s)' % ', '.join('%s' % s for s in self._type_signatures))
    if self._throws:
      output += (' throws %s' % ', '.join('%s' % s for s in self._throws))
    return 'MethodTypeSignature(%s)' % output

class ReturnType(object):
  """
    TypeSignature
    VoidSignature
  """
  @staticmethod
  def match(data):
    offset = 0
    type_sig, bytes_read = TypeSignature.match(data)
    if type_sig: return ReturnType(type_sig), bytes_read
    void_sig, bytes_read = VoidSignature.match(data)
    if void_sig: return ReturnType(void_sig), bytes_read
    return _UNPARSED

  def __init__(self, sig):
    self._signature = sig

  def __str__(self):
    return '%s' % self._signature

class ThrowsSignature(object):
  """
    '^' ClassTypeSignature
    '^' TypeVariableSignature
  """
  @staticmethod
  def match(data):
    offset = 0
    if data[offset] != '^':
      return _UNPARSED
    offset += 1
    cls_sig, bytes_read = ClassTypeSignature.match(data[offset:])
    if cls_sig: return cls_sig, bytes_read
    type_sig, bytes_read = TypeVariableSignature.match(data[offset:])
    if type_sig: return type_sig, bytes_read
    return _UNPARSED

class FormalTypeParameter(object):
  """
    Identifier ClassBound InterfaceBound*
  """
  @staticmethod
  def match(data):
    offset = 0
    identifier, bytes_read = Identifier.match(data[offset:])
    if identifier is None:
      return _UNPARSED
    else:
      pass
    offset += bytes_read

    class_bound, bytes_read = ClassBound.match(data[offset:])
    if class_bound is None:
      return _UNPARSED
    offset += bytes_read
    interfaces = []
    while True:
      interface, bytes_read = InterfaceBound.match(data[offset:])
      if interface is None:
        break
      offset += bytes_read
      interfaces.append(interface)
    return FormalTypeParameter(identifier, class_bound, interfaces), offset

  def __init__(self, identifier, class_bound, interface_bound=None):
    self._identifier = identifier
    self._class_bound = class_bound
    self._interface_bound = _list_if_none(interface_bound)

  def __str__(self):
    appendix = ''
    if self._interface_bound:
      appendix = 'interfaces:%s' % self._interface_bound
    return '%s extends %s%s' % (
      self._identifier,
      self._class_bound, appendix)

class FormalTypeParameters(object):
  """
    FormalTypeParameters:
      '<' FormalTypeParameter+ '>'
  """
  @staticmethod
  def match(data):
    offset = 0
    if data[offset] != '<': return _UNPARSED
    offset += 1
    parameters = []
    while data[offset] != '>':
      parameter, bytes_read = FormalTypeParameter.match(data[offset:])
      if parameter is None:
        return _UNPARSED
      parameters.append(parameter)
      offset += bytes_read
    # raise exception if len(parameters) == 0
    if len(parameters) == 0:
      raise ParseException('FormalTypeParameters requires >= 1 FormalTypeParameter')
    return parameters, offset + 1
