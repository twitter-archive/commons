def format_message(record):
  try:
    record_message = '%s' % (record.msg % record.args)
  except TypeError:
    record_message = record.msg
  return record_message
