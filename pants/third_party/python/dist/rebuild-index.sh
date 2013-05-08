#!/bin/sh

HERE=/pants/third_party/python/dist

cat - > index.html << HEADER
<html>
  <head>
    <title>Index of $HERE</title>
  </head>
  <body>
    <h1>Index of $HERE</h1>
HEADER

for egg in *.egg
do
  echo "<a href=\"$egg\">$egg</a>" >> index.html
done

cat - >> index.html << FOOTER
  </body>
</html>
FOOTER
