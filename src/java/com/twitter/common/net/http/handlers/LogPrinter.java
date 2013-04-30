package com.twitter.common.net.http.handlers;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import org.antlr.stringtemplate.StringTemplate;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;

import com.twitter.common.base.Closure;
import com.twitter.common.base.MorePreconditions;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Data;

/**
 * HTTP handler to page through log files. Supports GET and POST requests.  GET requests are
 * responsible for fetching chrome and javascript, while the POST requests are used to fetch actual
 * log data.
 */
public class LogPrinter extends StringTemplateServlet {
  private static final Logger LOG = Logger.getLogger(LogPrinter.class.getName());

  /**
   * A {@literal @Named} binding key for the log directory to display by default.
   */
  public static final String LOG_DIR_KEY =
        "com.twitter.common.net.http.handlers.LogPrinter.log_dir";

  private static final int DEFAULT_PAGE = 0;

  private static final int PAGE_CHUNK_SIZE_BYTES = Amount.of(512, Data.KB).as(Data.BYTES);
  private static final int TAIL_START_BYTES = Amount.of(10, Data.KB).as(Data.BYTES);
  private static final int PAGE_END_BUFFER_SIZE_BYTES = Amount.of(1, Data.KB).as(Data.BYTES);

  private static final String XML_RESP_FORMAT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                                              + "<logchunk text=\"%s\""
                                              + " end_pos=\"%d\">"
                                              + "</logchunk>";
  private final File logDir;

  @Inject
  public LogPrinter(@Named(LOG_DIR_KEY) File logDir, @CacheTemplates boolean cacheTemplates) {
    super("logprinter", cacheTemplates);
    this.logDir = Preconditions.checkNotNull(logDir);
  }

  /**
   * A POST request is made from javascript, to request the contents of a log file.  In order to
   * fulfill the request, the 'file' parameter must be set in the request.
   *
   * If file starts with a '/' then the file parameter will be treated as an absolute file path.
   * If file does not start with a '/' then the path will be assumed to be
   * relative to the log directory.
   *
   * @param req Servlet request.
   * @param resp Servlet response.
   * @throws ServletException If there is a problem with the servlet.
   * @throws IOException If there is a problem reading/writing data to the client.
   */
  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    resp.setContentType("text/xml; charset=utf-8");

    try {
      LogViewRequest request = new LogViewRequest(req);

      if (request.file == null) {
        // The log file is a required parameter for POST requests.
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }

      resp.setStatus(HttpServletResponse.SC_OK);
      PrintWriter responseBody = resp.getWriter();

      String responseXml = fetchXmlLogContents(request);
      responseBody.write(responseXml);
      responseBody.close();
    } catch (Exception e) {
      LOG.log(Level.SEVERE, "Unknown exception.", e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Fetches the chrome for the page.  If a file is requested, a page will be returned that uses an
   * AJAX request to fetch the log contents.  If no file is specified, then a file listing is
   * displayed.
   *
   * @param req Servlet request.
   * @param resp Servlet response.
   * @throws ServletException If there is a problem with the servlet.
   * @throws IOException If there is a problem reading/writing data to the client.
   */
  @Override
  protected void doGet(final HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    final LogViewRequest request = new LogViewRequest(req);

    if (request.download) {
      if (request.file == null) {
        resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "No file requested for download.");
        return;
      }

      if (!request.file.isRegularFile()) {
        resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Only regular files may be downloaded.");
        return;
      }

      try {
        OutputStream out = resp.getOutputStream();
        ServletContext context  = getServletConfig().getServletContext();
        String mimetype = context.getMimeType(request.file.getName());

        resp.setContentType(mimetype != null ? mimetype : "application/octet-stream" );
        resp.setContentLength((int) request.file.getFile().length());
        resp.setHeader("Content-Disposition", String.format("attachment; filename=\"%s\"",
            request.file.getName()));

        Files.copy(request.file.getFile(), out);
      } catch (Exception e) {
        resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to fetch file.");
        LOG.warning("Failed to download file " + request.file.getPath() + ": " + e.getMessage());
      }
    } else {
      writeTemplate(resp, new Closure<StringTemplate>() {
        @Override public void execute(StringTemplate template) {

          // TODO(William Farner): Consider using unix file utility to check if the requested file is a
          //    text file, and allow the user to download the file if it is not.
          if (request.isFileViewRequest()) {
            request.sendToTemplate(template);

            if (!request.tailing) {
              long readStartPos = getReadStartPos(request.file.getFile(), request.page);

              if (readStartPos > 0) template.setAttribute("prev", request.page + 1);
              if (request.page > 0) template.setAttribute("next", request.page - 1);
            }
          } else {
            // If a file was not requested, show a list of files.
            File dir = request.getListingDir();

            List<LogFile> logFiles = Lists.newArrayList();
            for (File file : dir.listFiles()) {
              logFiles.add(new LogFile(file));
            }

            // Sort by dir/file, subsort by name.
            Collections.sort(logFiles, new Comparator<LogFile>() {
              @Override public int compare(LogFile fileA, LogFile fileB) {
                if (fileA.isDir() == fileB.isDir()) {
                  return fileA.file.getName().compareTo(fileB.file.getName());
                } else {
                  return fileA.isDir() ? -1 : 1;
                }
              }
            });

            template.setAttribute("dir", dir);
            template.setAttribute("parent", dir.getParentFile());
            template.setAttribute("files", logFiles);
          }
        }
      });
    }
  }

  /**
   * Gets the starting position for reading a page from a file.
   *
   * @param file The file to find a page within.
   * @param page The page index, where page 0 is the last page (at the end of the file).
   * @return The byte index that the page begins on, or 0 if an invalid page number was provided.
   */
  private long getReadStartPos(File file, int page) {
    return page < 0 ? 0 : Math.max(0, file.length() - (page + 1) * PAGE_CHUNK_SIZE_BYTES);
  }

  /**
   * Stores request parameters and assigns default values.
   */
  private class LogViewRequest {
    public static final String DIR_PARAM = "dir";
    public static final String FILE_PARAM = "file";
    public static final String PAGE_PARAM = "page";
    public static final String FILTER_PARAM = "filter";
    public static final String TAIL_PARAM = "tail";
    public static final String START_POS_PARAM = "start_pos";
    public static final String DOWNLOAD_PARAM = "download";

    public final File dir;
    public final LogFile file;
    public final boolean download;
    public final int page;
    public final long startPos;
    public final String filter;
    public final boolean tailing;

    public LogViewRequest(HttpServletRequest req) {
      dir = req.getParameter(DIR_PARAM) == null ? null : new File(req.getParameter(DIR_PARAM));
      file = req.getParameter(FILE_PARAM) ==  null ? null
          : new LogFile(req.getParameter(FILE_PARAM));
      download = HttpServletRequestParams.getBool(req, DOWNLOAD_PARAM, false);
      tailing = HttpServletRequestParams.getBool(req, TAIL_PARAM, false);
      page = HttpServletRequestParams.getInt(req, PAGE_PARAM, DEFAULT_PAGE);
      Preconditions.checkArgument(page >= 0);

      startPos = HttpServletRequestParams.getLong(req, START_POS_PARAM, -1);
      if (file != null) {
        Preconditions.checkArgument(startPos >= -1 && startPos <= file.getFile().length());
      }
      filter = HttpServletRequestParams.getString(req, FILTER_PARAM, "");
    }

    public boolean isFileViewRequest() {
      return file != null && file.isRegularFile();
    }

    public File getListingDir() {
      if (file != null && file.getFile().isDirectory()) {
        return file.getFile();
      } else if (dir != null) {
        return dir;
      } else {
        return logDir;
      }
    }

    public void sendToTemplate(StringTemplate template) {
      template.setAttribute(FILE_PARAM, file);
      template.setAttribute(PAGE_PARAM, page);
      template.setAttribute(FILTER_PARAM, filter);
      template.setAttribute(TAIL_PARAM, tailing);
    }
  }

  /**
   * Class to wrap a log file and offer functions to StringTemplate via reflection.
   */
   @VisibleForTesting
   class LogFile {
    private final File file;

    public LogFile(File file) {
      this.file = file;
    }

    public LogFile(String filePath) {
      MorePreconditions.checkNotBlank(filePath, "filePath must not be null or empty");
      this.file = filePath.startsWith("/") ? new File(filePath) : new File(logDir, filePath);
    }

    public File getFile() {
      return file;
    }

    public boolean isDir() {
      return !isRegularFile();
    }

    public boolean isRegularFile() {
      return file.isFile();
    }

    public String getPath() {
      return file.getAbsolutePath();
    }

    public String getName() {
      return file.getName();
    }

    public String getUrlpath() throws UnsupportedEncodingException {
      return URLEncoder.encode(getPath(), Charsets.UTF_8.name());
    }

    public String getSize() {
      Amount<Long, Data> length = Amount.of(file.length(), Data.BYTES);

      if (length.as(Data.GB) > 0) {
        return length.as(Data.GB) + " GB";
      } else if (length.as(Data.MB) > 0) {
        return length.as(Data.MB) + " MB";
      } else if (length.as(Data.KB) > 0) {
        return length.as(Data.KB) + " KB";
      } else {
        return length.getValue() + " bytes";
      }
    }
  }

  /**
   * Reads data from a log file and prepares an XML response which includes the (sanitized) log text
   * and the last position read from the file.
   *
   * @param request The request parameters.
   * @return A string containing the XML-formatted response.
   * @throws IOException If there was a problem reading the file.
   */
  private String fetchXmlLogContents(LogViewRequest request) throws IOException {
    RandomAccessFile seekFile = new RandomAccessFile(request.file.getFile(), "r");
    try {
      // Move to the approximate start of the page.
      if (!request.tailing) {
        seekFile.seek(getReadStartPos(request.file.getFile(), request.page));
      } else {
        if (request.startPos < 0) {
          seekFile.seek(Math.max(0, request.file.getFile().length() - TAIL_START_BYTES));
        } else {
          seekFile.seek(request.startPos);
        }
      }

      byte[] buffer = new byte[PAGE_CHUNK_SIZE_BYTES];
      int bytesRead = seekFile.read(buffer);
      long chunkStop = seekFile.getFilePointer();
      StringBuilder fileChunk = new StringBuilder();
      if (bytesRead > 0) {
        fileChunk.append(new String(buffer, 0, bytesRead));

        // Read at most 1 KB more while searching for another line break.
        buffer = new byte[PAGE_END_BUFFER_SIZE_BYTES];
        int newlinePos = 0;
        bytesRead = seekFile.read(buffer);
        if (bytesRead > 0) {
          for (byte b : buffer) {
            newlinePos++;
            if (b == '\n') break;
          }

          fileChunk.append(new String(buffer, 0, newlinePos));
          chunkStop = seekFile.getFilePointer() - (bytesRead - newlinePos);
        }
      }

      return logChunkXml(filterLines(fileChunk.toString(), request.filter), chunkStop);
    } finally {
      seekFile.close();
    }
  }

  private static String sanitize(String text) {
    text = StringEscapeUtils.escapeHtml(text);

    StringBuilder newString = new StringBuilder();
    for (char ch : text.toCharArray()) {
      if ((ch > 0x001F && ch < 0x00FD) || ch == '\t' || ch == '\r') {
        // Directly include anything from 0x1F (SPACE) to 0xFD (tilde)
        // as well as tab and carriage-return.
        newString.append(ch);
      } else {
        // Encode everything else.
        newString.append("&#").append((int) ch).append(";");
      }
    }
    return StringEscapeUtils.escapeXml(newString.toString());
  }

  private String logChunkXml(String text, long lastBytePosition) {
    return String.format(XML_RESP_FORMAT, sanitize(text) , lastBytePosition);
  }

  @VisibleForTesting
  protected static String filterLines(String text, String filterRegexp) {
    if (StringUtils.isEmpty(filterRegexp)) return text;

    List<String> lines = Lists.newArrayList(text.split("\n"));
    final Pattern pattern = Pattern.compile(filterRegexp);

    Iterable<String> filtered = Iterables.filter(lines, new Predicate<String>() {
      @Override public boolean apply(String line) {
        return pattern.matcher(line).matches();
      }
    });

    return Joiner.on("\n").join(filtered);
  }

  private class LogConfigException extends Exception {
    public LogConfigException(String message) {
      super(message);
    }
  }
}
