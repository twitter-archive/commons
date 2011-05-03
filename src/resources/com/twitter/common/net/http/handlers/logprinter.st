<!--
=================================================================================================
Copyright 2011 Twitter, Inc.
-------------------------------------------------------------------------------------------------
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this work except in compliance with the License.
You may obtain a copy of the License in the LICENSE file, or at:

 http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
=================================================================================================
-->

<html>
  <title>Log Printer</title>
  <body>
    $if(file)$
      <h3>$file.path$</h3>

      $if(prev)$
        <a href='?file=$file.urlpath$&page=$prev$&filter=$filter$'>Prev</a>
      $else$
        Prev
      $endif$
      $if(next)$
        <a href='?file=$file.urlpath$&page=$next$&filter=$filter$'>Next</a>
      $else$
        Next
      $endif$

      <div align='right'>
        <form action='' method='get'>
          <input type='hidden' name='file' value='$file.path$'>
          <input type='hidden' name='page' value='$page$'>

          <table border=0 cellpadding=0 cellspacing=0 align=right>
            <tr>
              <td>Regexp Filter: </td>
              <td><input type='text' name='filter' value='$filter$'>
                  <input type='submit' value='Go'>
              </td>
            </tr>
          </table>
        </form>
      </div>

      <br/>
      <hr>
      <br/>
      <pre id='log_contents'>
        <!-- Will be populated with log contents when AJAX call returns -->
      </pre>
      <br/>
      <hr>
      <br/>

      <script type='text/javascript'>
        var intervalId;

        function toggleTailState(tailCheckbox) {
          if (tailCheckbox.checked) {
            startTailing();
          } else {
            clearInterval(intervalId);
          }

          loc = '?file=$file.urlpath$&page=$page$&filter=$filter$';
          if (tailCheckbox.checked) {
            loc = loc + '&tail=true';
          }
          window.location = loc;
        }

        function fetchLogContents() {
          if (outstandingRequest) return;
          outstandingRequest = true;

          var req = new XMLHttpRequest();
          req.onreadystatechange = getReadyStateHandler(req, populateLog);

          // Open an HTTP POST connection to the logs servlet.
          // Third parameter specifies request is asynchronous.
          req.open("POST", "logs", true);

          // Specify that the body of the request contains form data
          req.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");

          // Send form encoded data that specifies the request.
          req.send("file=$file.urlpath$&page=$page$&filter=$filter$&tail=$tail$&start_pos=" + tailEndPosition);
        }

        function populateLog(logXml) {
          outstandingRequest = false;
          var logChunk = logXml.getElementsByTagName("logchunk")[0];
          if ($tail$) {
            logDisplay = document.getElementById("log_contents").innerHTML + logChunk.getAttribute("text");
          } else {
            logDisplay = logChunk.getAttribute("text");
          }

          document.getElementById("log_contents").innerHTML = logDisplay;
          tailEndPosition = logChunk.getAttribute("end_pos");
          document.body.scrollTop = document.body.scrollHeight;
        }

        function startTailing() {
          intervalId = setInterval(fetchLogContents, 1000);
        }

        /**
         * Returns a function that waits for the specified XMLHttpRequest
         * to complete, then passes its XML response
         * to the given handler function.
         * req - The XMLHttpRequest whose state is changing
         * responseXmlHandler - Function to pass the XML response to
         */
        function getReadyStateHandler(req, responseXmlHandler) {
          // Return an anonymous function that listens to the
          // XMLHttpRequest instance
          return function () {
            // If the request's status is "complete"
            if (req.readyState == 4) {
              // Check that a successful server response was received
              if (req.status == 200) {
                // Pass the XML payload of the response to the
                // handler function
                responseXmlHandler(req.responseXML);

              } else {
                // An HTTP problem has occurred
                alert("HTTP error: "+req.status);
              }
            }
          }
        }

        var tailMode = false;
        var outstandingRequest = false;
        var tailEndPosition = -1;
        if ($tail$) {
          startTailing();
        } else {
          fetchLogContents();
        }
      </script>

      $if(prev)$
        <a href='?file=$file.urlpath$&page=$prev$&filter=$filter$'>Prev</a>
      $else$
        Prev
      $endif$
      $if(next)$
        <a href='?file=$file.urlpath$&page=$next$&filter=$filter$'>Next</a>
      $else$
        Next
      $endif$

      <div align='right'>
        <input type='checkbox' id='tail' $if(tail)$ checked $endif$ onclick='toggleTailState(this);'>
        <label for='tail'>Tail log output</label>
      </div>
    $else$
      <h3>$dir.absolutePath$</h3>

      <table border=0 cellpadding=0 cellspacing=5 align=left>
        $if(parent)$
          <tr>
            <td></td>
            <td><a href='?file=$parent$'>../</a></td>
          </tr>
        $endif$

        $files:{
          <tr>
            <td>
              $if(it.regularFile)$
                <a href='?file=$it.urlpath$&download=true'><font size=1>DL</font></a>
              $endif$
            </td>
            <td>
              <a href='?file=$it.urlpath$'>$it.name$$if(it.dir)$/$endif$</a>
               $if(it.regularFile)$ ($it.size$) $endif$
            </td>
          </tr>
        }$
      </table>
    $endif$

    $if(exception)$
      <font color='red'>Exception: $exception$</font>
    $endif$
  </body>
</html>
