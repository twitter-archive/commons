import os

def du(directory):
  size = 0
  for root, _, files in os.walk(directory):
    size += sum(os.path.getsize(os.path.join(root, file)) for file in files)
  return size
