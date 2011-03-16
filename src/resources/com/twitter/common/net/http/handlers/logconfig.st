<!--
====================================================================================================
Copyright 2011 Twitter, Inc.
____________________________________________________________________________________________________
Licensed to the Apache Software Foundation (ASF) under one or more contributor license
agreements.  See the NOTICE file distributed with this work for additional information regarding
copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with the License.  You may
obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the
License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
express or implied.  See the License for the specific language governing permissions and
limitations under the License.
====================================================================================================
-->

<html>
  <title>Logger Config</title>
  <body>
    <form action='' method='post'>
      <select name='logger'>
        $loggers:{<option value='$it.name$'>$it.name$}$
      </select>
      <select name='level'>
        $levels:{<option value='$it$'>$it$}$
      </select>
      <input type='submit' value='Go'>
    </form>

    $if(configChange)$
      <br />
      <i>$configChange$</i>
      <br />
    $endif$

    <table border=1 cellpadding=0 cellspacing=0 align=left>
      <tr>
        <th>Logger</th>
        <th>Level</th>
      </tr>
      $loggers:{<tr><td>$it.name$</td><td>$it.level$</td></tr>}$
    </table>
  </body>
</html>
