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

package com.twitter.common.net.http.handlers.pprof;

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.base.Preconditions;
import com.google.common.io.Closeables;

import java.util.concurrent.TimeUnit;

import com.twitter.common.net.http.handlers.HttpServletRequestParams;
import com.twitter.jvm.CpuProfile;
import com.twitter.util.Duration;
import com.twitter.util.Duration$;

/**
 * A handler that collects thread profile information for the running application and replies
 * in a format recognizable by <a href="http://code.google.com/p/gperftools">gperftools</a>.
 * <p>
 * This class is abstract because it requires a particular {@link Thread.State} to profile.
 */
abstract class ProfileHandler extends HttpServlet {
  private static final Logger LOG = Logger.getLogger(ProfileHandler.class.getName());

  private final Thread.State stateToProfile;

  protected ProfileHandler(Thread.State stateToProfile) {
    this.stateToProfile = Preconditions.checkNotNull(stateToProfile);
  }

  @Override
  protected final void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {

    final int profileDurationSecs = HttpServletRequestParams.getInt(req, "seconds", 10);
    final int profilePollRate = HttpServletRequestParams.getInt(req, "hz", 100);
    LOG.info("Collecting CPU profile for " + profileDurationSecs + " seconds at "
        + profilePollRate + " Hz");

    Duration sampleDuration = Duration$.MODULE$.fromTimeUnit(profileDurationSecs, TimeUnit.SECONDS);
    CpuProfile profile =
        CpuProfile.recordInThread(sampleDuration, profilePollRate, stateToProfile).get();
    resp.setHeader("Content-Type", "pprof/raw");
    resp.setStatus(HttpServletResponse.SC_OK);
    OutputStream responseBody = resp.getOutputStream();
    try {
      profile.writeGoogleProfile(responseBody);
    } finally {
      Closeables.close(responseBody, /* swallowIOException */ true);
    }
  }
}
