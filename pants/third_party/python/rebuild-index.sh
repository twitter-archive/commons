#!/bin/sh

START=$PWD

HERE=$(cd $(dirname $(readlink $0 || echo $0)) && pwd)
cd $HERE

OUT=index.html
SDIST_ROOT=/pants/third_party/python

cat > $OUT << HEADER
<html>
  <head>
    <title>Index of $SDIST_ROOT</title>
  </head>
  <body>
    <h1>Index of $SDIST_ROOT</h1>
HEADER

for sdist in *.tar.gz *.zip *.tgz
do
  if [ -r "$sdist" ]
  then
    echo "    <a href=\"$sdist\">$sdist</a>" >> $OUT
  fi
done

cat >> $OUT << FOOTER
  </body>
</html>
FOOTER

cd $START
