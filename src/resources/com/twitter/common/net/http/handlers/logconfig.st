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
  <title>Logger Config</title>
    <head>
      <script src="//ajax.googleapis.com/ajax/libs/jquery/1.11.2/jquery.min.js"></script>
    </head>
    <body>
      <table border=1 cellpadding=0 cellspacing=0 align=left>
        <tr>
          <th>Logger</th>
          <th>Level</th>
        </tr>
        $loggers:{ logger |    
          <tr>
            <td>$logger.name$</td>
            <td>
              <select id="$logger.name$" name="level"  class="selectlevels" data-loglevel="$logger.level$">
                $levels:{ level |
                  <option value='$level$'>$level$</option>	
                }$
              </select>
            </td>
          </tr>
        }$
      </table>
      <script>
        \$(document).ready(function() {
          // For every logger changed the select to match the current level.
          \$('.selectlevels').each(function() { 
            \$(this).val(\$(this).data('loglevel')); 
          });
          // Every time a logger value is changed post the logger name and new level.        
          \$('.selectlevels').change(function() { 
            \$.post("/logconfig", 
                   { logger : \$(this).attr('id'), level : \$(this).val() },
                   function() {
                     // Reload the page so the new logger value is visible.  
                     location.reload();
                   }
            );	
          });
        });
      </script>
    </body>
</html>
