from copy import copy
from datetime import datetime
from optparse import Option, OptionValueError

def _check_date(option, opt, value):
  try:
    return datetime.strptime(value, '%Y-%m-%d').date()
  except ValueError:
    raise OptionValueError('Value for %s not a valid date in format YYYY-MM-DD' % option)

class TwitterOption(Option):
  TYPES = Option.TYPES + ('date',)
  TYPE_CHECKER = copy(Option.TYPE_CHECKER)
  TYPE_CHECKER['date'] = _check_date
