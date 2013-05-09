#!/bin/sh

START=$PWD

HERE=$(cd $(dirname $(readlink $0 || echo $0)) && pwd)
cd $HERE

OUT=index.html
DIST_ROOT=/pants/third_party/python/dist

cat > $OUT << HEADER
<html>
  <head>
    <title>Index of $DIST_ROOT</title>
  </head>
  <body>
    <h1>Index of $DIST_ROOT</h1>
HEADER

for egg in *.egg
do
  echo "<a href=\"$egg\">$egg</a>" >> $OUT
done

cat >> $OUT << FOOTER
  </body>
</html>
FOOTER

cd $START
