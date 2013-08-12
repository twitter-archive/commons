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

// TODO(William Farner): A unit test is sorely needed here.  Add a unit test for jsirois to use
// as a starter for pants support for jsunit.

// Declare namespace.
parser = {};

parser.Abs = function(args) {
  if (args.length != 1) {
    throw "abs() accepts exactly one argument.";
  }
  this.arg = args[0];
};
parser.Abs.help = "abs(num)";
parser.Abs.prototype.evaluate = function(vars) {
  return Math.abs(this.arg.evaluate(vars));
};


parser.Ceil = function(args) {
  if (args.length != 1) {
    throw "ceil() accepts exactly one argument.";
  }
  this.arg = args[0];
};
parser.Ceil.help = "ceil(num)";
parser.Ceil.prototype.evaluate = function(vars) {
  return Math.ceil(this.arg.evaluate(vars));
};


parser.Floor = function(args) {
  if (args.length != 1) {
    throw "floor() accepts exactly one argument.";
  }
  this.arg = args[0];
};
parser.Floor.help = "floor(num)";
parser.Floor.prototype.evaluate = function(vars) {
  return Math.floor(this.arg.evaluate(vars));
};


parser.Log = function(args) {
  if (args.length != 1) {
    throw "log() accepts exactly one argument.";
  }
  this.arg = args[0];
};
parser.Log.help = "log(num)";
parser.Log.prototype.evaluate = function(vars) {
  return Math.log(this.args.evaluate(vars));
};


parser.Rate = function(args) {
  if (args.length == 1) {
    this.winLen = 1;
    this.arg = args[0];
  } else if (args.length == 2) {
    if (!(args[0] instanceof parser.Constant)) {
      throw "the first argument to rate() must be a constant.";
    }
    this.winLen = args[0].c;
    this.arg = args[1];
  } else {
    throw "rate() accepts one or two arguments.";
  }

  this.samples = [];
  this.timeInput = new parser.Var(-1, "time");
};
parser.Rate.help = "rate([window size,] var)";
parser.Rate.prototype.evaluate = function(vars) {
  var newY = this.arg.evaluate(vars);
  var newT = this.timeInput.evaluate(vars);
  this.samples.push([newY, newT]);
  var oldest = this.samples[0];
  if (this.samples.length > this.winLen) {
    this.samples.splice(0, this.samples.length - this.winLen);
  }
  var denom = newT - oldest[1];
  // Assumes time unit is milliseconds.
  return (denom == 0) ? 0 : ((1000 * (newY - oldest[0])) / denom);
};


parser.Round = function(args) {
  if (args.length != 1) {
    throw "round() accepts exactly one argument.";
  }
  this.arg = args[0];
};
parser.Round.help = "round(num)";
parser.Round.prototype.evaluate = function(vars) {
  return Math.round(this.arg.evaluate(vars));
};


parser.Sqrt = function(args) {
  if (args.length != 1) {
    throw "sqrt() accepts exactly one argument.";
  }
  this.arg = args[0];
};
parser.Sqrt.help = "sqrt(num)";
parser.Sqrt.prototype.evaluate = function(vars) {
  return Math.sqrt(this.arg.evaluate(vars));
};


parser.functions = {
  'abs':    parser.Abs,
  'ceil':   parser.Ceil,
  'floor':  parser.Floor,
  'log':    parser.Log,
  'rate':   parser.Rate,
  'round':  parser.Round,
  'sqrt':   parser.Sqrt
};


parser.Operator = function(evaluator) {
  this.evaluator = evaluator;
};
parser._operators = {
    '+': new parser.Operator(function(a, b) { return a + b; }),
    '-': new parser.Operator(function(a, b) { return a - b; }),
    '*': new parser.Operator(function(a, b) { return a * b; }),
    '/': new parser.Operator(function(a, b) { return a / b; })
};


parser.Token = function(start, text) {
  this.start = start;
  this.text = text;
};


parser.Part = function(start) {
  this.start = start;
};
parser.Part.prototype.getVars = function() {
  return [];
};


parser.MetaPart = function(start, args) {
  this.Part = parser.Part;
  this.Part(start);
  this.args = args || [];
};
parser.MetaPart.prototype.getVars = function() {
  var all = [];
  $.each(this.args, function(i, arg) {
    all = all.concat(arg.getVars());
  });
  return all;
};


parser.Function = function(start, evaluator, args) {
  this.MetaPart = parser.MetaPart;
  this.MetaPart(start, args);
  this.evaluator = evaluator;
};
parser.Function.prototype = new parser.MetaPart();
parser.Function.prototype.evaluate = function(vars) {
  return this.evaluator.evaluate(vars);
};


parser.Operation = function(start, op) {
  this.MetaPart = parser.MetaPart;
  this.MetaPart(start, []);
  this.op = op;
};
parser.Operation.prototype = new parser.MetaPart();
parser.Operation.prototype.evaluate = function(vars) {
  var result = this.args[0].evaluate(vars);
  for (var i = 1; i < this.args.length; i++) {
    result = this.op.evaluator(result, this.args[i].evaluate(vars));
  }
  return result;
};


parser.Constant = function(start, c) {
  this.Part = parser.Part;
  this.Part(start);
  this.c = parseFloat(c);
};
parser.Constant.prototype = new parser.Part();
parser.Constant.prototype.evaluate = function() {
  return this.c;
};


parser.Var = function(start, name) {
  this.Part = parser.Part;
  this.Part(start);
  this.name = name;
};
parser.Var.prototype.evaluate = function(vars) {
  // TODO(William Farner): Clean this up - currently it's reaching out
  // to state within grapher.js.
  return vars[metrics[this.name]];
};
parser.Var.prototype.getVars = function() {
  return [this.name];
};


parser.tokenize = function(str, offset, isDelimiter) {
  if (offset === undefined) {
    offset = 0;
  }

  var level = 0;
  var start = 0;
  var tokens = [];
  for (var i = 0; i < str.length; i++) {
    var c = str.charAt(i);
    if (c == '(') {
      level += 1;
      continue;
    } else if (c == ')') {
      level -= 1;
      continue;
    }

    if (level == 0) {
      if (isDelimiter(c)) {
        var token = str.substring(start, i);
        if (token.length == 0) {
          addError(str, 'Missing operand', i + offset);
        }
        tokens.push(new parser.Token(start + offset, token));
        tokens.push(new parser.Token(start, c));
        start = i + 1;
      }
    }
  }

  var token = str.substring(start);
  if (token.length == 0) {
    addError(str, 'Expected expression but found operator', start + offset);
  }
  tokens.push(new parser.Token(start + offset, str.substring(start)));

  return tokens;
};

var _FUNCTION_CALL_RE = /^(\w+)\((.*)\)$/g;
var _SUB_EXPRESSION_RE = /^\((.*)\)$/g;
var _PAREN_RE = /([\(\)])/;

parser.parse = function(query, offset) {
  // Split the expression at operator boundaries in the top-level scope.
  var tokens = parser.tokenize(query, offset, function(c) {
    return parser._operators[c];
  });
  tokens = $.map(tokens, function(token) {
    var op = parser._operators[token.text];
    return op ? new parser.Operation(token.start, op) : token;
  });

  var result = [];
  $.each(tokens, function(i, token) {
    if (token instanceof parser.Operation) {
      token.args.push(result.splice(result.length - 1, 1)[0]);
      result.push(token);
      return;
    }

    // Match a function call.
    var parsed;
    var match = _FUNCTION_CALL_RE.exec(token.text);
    if (match) {
      var f = match[1];
      var arg = match[2];
      if (!parser.functions[f]) {
        addError(query, 'Unrecognized function ' + f, token.start);
      }
      var parsedArg = parser.parse(arg, token.start + 1);
      // Split and parse function args.
      var argTokens = parser.tokenize(arg, token.start + 1, function(c) { return c == ','; });
      argTokens = $.grep(argTokens, function(argToken) { return argToken.text != ','; });
      var parsedArgs = $.map(argTokens, function(argToken) {
        return parser.parse(argToken.text, argToken.start);
      });
      parsed = new parser.Function(
          token.start,
          new parser.functions[f](parsedArgs), parsedArgs);
    } else {
      // Match a sub expression.
      match = _SUB_EXPRESSION_RE.exec(token.text);
      if (match) {
        parsed = parser.parse(match[1], token.start + 1);
      } else {
        match = _PAREN_RE.exec(token.text);
        if (match) {
          addError(query, 'Unmatched paren', token.start + token.text.indexOf(match[1]));
        }
        if (isNaN(token.text)) {
          parsed = new parser.Var(token.start, token.text);
        } else {
          parsed = new parser.Constant(token.start, token.text);
        }
      }
    }

    var lastResult = result.length == 0 ? null : result[result.length - 1];
    if (lastResult instanceof parser.Operation) {
      lastResult.args.push(parsed);
    } else {
      result.push(parsed);
    }
  });

  if (result.length != 1) {
    throw 'Unexpected state.';
  }
  return result[0];
};
