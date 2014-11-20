package com.twitter.common.net.http.handlers;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;

import com.twitter.common.stats.Stat;

/**
 * A servlet that returns the current value of all variables in JSON format.
 * The format returns a JSON object with string fields and typed values:
 * <pre>
 *   {
 *     "var_a": 1,
 *     "var_b": 126.0,
 *     "var_c": "a string value",
 *   }
 * </pre>
 * If the optional URL parameter 'pretty' is used, the output will be pretty-printed
 * (similar to the above example).
 *
 * @author William Farner
 */
public class VarsJsonHandler extends HttpServlet {

  private final Supplier<Iterable<Stat<?>>> statSupplier;

  /**
   * Creates a new handler that will report stats from the provided supplier.
   *
   * @param statSupplier Stats supplier.
   */
  @Inject
  public VarsJsonHandler(Supplier<Iterable<Stat<?>>> statSupplier) {
    this.statSupplier = Preconditions.checkNotNull(statSupplier);
  }

  @VisibleForTesting
  String getBody(boolean pretty) {
    Map<String, Object> vars = Maps.newLinkedHashMap();
    for (Stat<?> var : statSupplier.get()) {
      vars.put(var.getName(), var.read());
    }
    return getGson(pretty).toJson(vars);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {

    resp.setContentType("application/json");
    resp.setStatus(HttpServletResponse.SC_OK);
    PrintWriter responseBody = resp.getWriter();
    try {
      responseBody.print(getBody(req.getParameter("pretty") != null));
    } finally {
      responseBody.close();
    }
  }

  private Gson getGson(boolean pretty) {
    return pretty ? new GsonBuilder().setPrettyPrinting().create() : new Gson();
  }
}
