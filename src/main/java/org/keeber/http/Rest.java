package org.keeber.http;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

/**
 * A very simple single class rest client.
 * 
 * 
 * @author Jason Keeber <jason@keeber.org>
 *
 */
public class Rest {
  private String baseUrl;
  private Gson serializer;
  private Map<String, String> headers = new HashMap<String, String>();
  private static final String AUTH = "Authorization";

  private Rest(String url) {
    this.baseUrl = url;
  }

  public static Rest withURL(String url) {
    return new Rest(url);
  }

  private enum Method {
    POST, GET, PUT, DELETE, HEAD;
  }

  public Rest header(String key, String value) {
    headers.put(key, value);
    return this;
  }

  public Rest clear(String key) {
    headers.remove(key);
    return this;
  }

  public Rest nobasic() {
    return clear(AUTH);
  }

  public Rest basic(String username, String password) {
    return header(AUTH, "Basic " + utils.encode(username + ":" + password));
  }

  /**
   * Sets headers for JSON communication (Accept & Content-Type).
   * 
   * @return Rest
   */
  public Rest json() {
    header("Accept", "application/json");
    header("Content-Type", "application/json");
    return this;
  }

  private Gson serializer() {
    return this.serializer == null ? serializer = new GsonBuilder().setPrettyPrinting().create() : serializer;
  }

  public Rest serializer(Gson serializer) {
    this.serializer = serializer;
    return this;
  }

  public Request request(String url) {
    return new Request(url);
  }

  @SuppressWarnings("unchecked")
  private <T> Response<T> execute(Request request, Object body, Method method, Type type, String... routes) throws RestException {
    Response<T> response = new Response<T>();
    HttpURLConnection connection = null;
    boolean streaming = type.equals(InputStream.class), multipart = body != null && body instanceof Form;
    try {
      // Swap the route params into the url
      String url = new StringBuilder(baseUrl).append(request.url).append(request.query == null ? "" : request.query).toString();
      if (routes.length % 2 != 0) {
        throw new RestException("Even number of route paramaters expected [" + routes.length + "]");
      }
      for (int i = 0; i < routes.length; i += 2) {
        url = url.replaceAll("(%7B|\\{)" + routes[i] + "(%7D|\\})", URLEncoder.encode(routes[i + 1], utils.UTF_8));
      }
      // Create the connection & set the method
      connection = (HttpURLConnection) new URL(url).openConnection();
      connection.setRequestMethod(method.toString());
      // Set the headers
      {
        for (Entry<String, String> header : headers.entrySet()) {
          connection.setRequestProperty(header.getKey(), header.getValue());
        }
        for (Entry<String, String> header : request.headers.entrySet()) {
          connection.setRequestProperty(header.getKey(), header.getValue());
        }
      }

      //
      String boundary = null;
      if (multipart) {
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + (boundary = "==" + System.currentTimeMillis() + "=="));
      }
      //
      connection.setDoInput(true);
      connection.setDoOutput(body != null);
      connection.connect();
      // Request BODY
      if (body != null) {
        if (multipart) {
          Form form = (Form) body;
          OutputStream os;
          PrintWriter wt = new PrintWriter(new OutputStreamWriter(os = new BufferedOutputStream(connection.getOutputStream()), utils.UTF_8), true);
          for (Entry<String, Object> entry : form.data.entrySet()) {
            if (entry.getValue() instanceof Form.FileEntry) {
              Form.FileEntry fileEntry = (Form.FileEntry) entry.getValue();
              wt.append("--" + boundary).append(utils.LF);
              wt.append("Content-Disposition: form-data; name=\"").append(entry.getKey()).append("\"; filename=\"").append(fileEntry.filename).append("\"").append(utils.LF);
              wt.append("Content-Type: ").append(HttpURLConnection.guessContentTypeFromName(fileEntry.filename)).append(utils.LF);
              wt.append("Content-Transfer-Encoding: binary").append(utils.LF).append(utils.LF);
              wt.flush();
              utils.copy(fileEntry.stream, os, false);
              utils.close(fileEntry.stream);
              wt.append(utils.LF);
              wt.flush();
            } else {
              wt.append("--" + boundary).append(utils.LF);
              wt.append("Content-Disposition: form-data; name=\"").append(entry.getKey()).append("\"").append(utils.LF);
              wt.append("Content-Type: text/plain; charset=").append(utils.UTF_8).append(utils.LF).append(utils.LF);
              wt.append(entry.getValue() instanceof String ? entry.getValue() + "" : serializer().toJson(entry.getValue()));
              wt.append(utils.LF).flush();
            }
          }
          wt.append("--" + boundary + "--").append(utils.LF).flush();
          wt.close();
        } else {
          InputStream is;
          if (body instanceof InputStream) {
            is = (InputStream) body;
          } else {
            is = new ByteArrayInputStream((body instanceof String ? (String) body : serializer().toJson(body)).getBytes(utils.UTF_8));
          }
          utils.copy(is, connection.getOutputStream(), true);
        }
      }
      response.code = connection.getResponseCode();
      response.message = connection.getResponseMessage();
      response.length = streaming ? connection.getContentLength() : -1;
      if (response.code >= 200 && response.code < 400) {
        if (streaming) {
          response.result = (T) new AutocloseConnectionStream(connection, connection.getInputStream());
        } else {
          String result = utils.asString(connection.getInputStream());
          response.result = (T) (type.equals(String.class) ? result : serializer().fromJson(result, type));
        }
      }
    } catch (ProtocolException e) {
      throw new RestException(e.getMessage(), e);
    } catch (MalformedURLException e) {
      throw new RestException(e.getMessage(), e);
    } catch (IOException e) {
      throw new RestException(e.getMessage(), e);
    } finally {
      if (!streaming && connection != null) {
        connection.disconnect();
      }
    }
    return response;
  }

  /**
   * The HTTP request class - can be reused once created ie: the get() stream() post() methods can
   * be called repeatedly.
   * 
   * The default header from the parent client are added when the request is executed.
   * 
   * @author Jason Keeber <jason@keeber.org>
   *
   */
  public class Request {
    private String url, query;
    private Map<String, String> headers = new HashMap<String, String>();

    private Request(String url) {
      this.url = url;
    }

    public Request header(String key, String value) {
      headers.put(key, value);
      return this;
    }

    public Request clear(String key) {
      headers.remove(key);
      return this;
    }

    public Request basic(String username, String password) {
      return header(AUTH, "Basic " + utils.encode(username + ":" + password));
    }

    public Request nobasic() {
      return clear(AUTH);
    }

    public Request query(String name, String value) {
      try {
        query = (query == null ? "?" : query + "&") + name + "=" + URLEncoder.encode(value, utils.UTF_8);
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e.getMessage(), e);
      }
      return this;
    }

    /**
     * Execute a GET transformed to the expected type.
     * 
     * @param type expected return type or class.
     * @return
     * @throws RestException
     */
    public <T> Response<T> get(Type type, String... routes) throws RestException {
      return execute(this, null, Method.GET, type, routes);
    }

    /**
     * Execute a GET as a JsonElement
     * 
     * @return
     * @throws RestException
     */
    public Response<JsonElement> get(String... routes) throws RestException {
      return get(JsonElement.class, routes);
    }

    /**
     * Stream a GET
     * 
     * @return
     * @throws RestException
     */
    public Response<InputStream> stream(String... routes) throws RestException {
      return get(InputStream.class, routes);
    }

    public <T> Response<T> head(Type type, String... routes) throws RestException {
      return execute(this, null, Method.HEAD, type, routes);
    }

    public Response<JsonElement> head(String... routes) throws RestException {
      return head(JsonElement.class, routes);
    }

    public <T> Response<T> post(Object body, Type type, String... routes) throws RestException {
      return execute(this, body, Method.POST, type, routes);
    }

    public Response<JsonElement> post(Object body, String... routes) throws RestException {
      return post(body, JsonElement.class, routes);
    }

    public <T> Response<T> put(Object body, Type type, String... routes) throws RestException {
      return execute(this, body, Method.PUT, type, routes);
    }

    public Response<JsonElement> put(Object body, String... routes) throws RestException {
      return put(body, JsonElement.class, routes);
    }

    public <T> Response<T> delete(Type type, String... routes) throws RestException {
      return execute(this, null, Method.DELETE, type, routes);
    }

    public Response<JsonElement> delete(String... routes) throws RestException {
      return delete(JsonElement.class, routes);
    }

  }

  public class Response<T> {
    private T result;
    private int code, length;
    private String message;

    public boolean hasResult() {
      return result != null;
    }

    public T getResult() {
      return result;
    }

    public int getCode() {
      return code;
    }

    public int getLength() {
      return length;
    }

    public String getMessage() {
      return message;
    }
  }

  /**
   * A stream that disconnection the Http URL Connection when it is closed.
   * 
   * @author Jason Keeber <jason@keeber.org>
   *
   */
  private class AutocloseConnectionStream extends FilterInputStream {
    private transient HttpURLConnection connection;

    public AutocloseConnectionStream(HttpURLConnection connection, InputStream in) {
      super(in);
      this.connection = connection;
    }

    @Override
    public void close() throws IOException {
      super.close();
      connection.disconnect();
    }

  }

  /**
   * Oh noes...it haz a went wrong.
   * 
   * @author Jason Keeber <jason@keeber.org>
   *
   */
  public static class RestException extends Exception {

    public RestException(String message, Throwable cause) {
      super(message, cause);
    }

    public RestException(String message) {
      super(message);
    }

  }

  /**
   * Internal utility class.
   * 
   * @author Jason Keeber <jason@keeber.org>
   *
   */
  public static class utils {
    private static final String UTF_8 = "UTF-8";
    private static final String LF = "\r\n";

    public static void copy(InputStream is, OutputStream os, boolean close) throws IOException {
      try {
        byte[] buffer = new byte[1024 * 16];
        int len;
        while ((len = is.read(buffer)) > 0) {
          os.write(buffer, 0, len);
        }
        os.flush();
      } finally {
        if (close) {
          utils.close(is);
          utils.close(os);
        }
      }
    }

    public static String asString(InputStream is) throws IOException {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      copy(is, bos, true);
      return bos.toString(UTF_8);
    }

    private static void close(Closeable stream) {
      if (stream != null) {
        try {
          stream.close();
        } catch (IOException e) {
          // Ignore (that is the only function of this method.
        }
      }
    }

    private static String encode(String content) {
      byte[] data;
      try {
        data = content.getBytes(UTF_8);
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e.getMessage(), e);
      }
      char[] tbl = {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u',
          'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/'};

      StringBuilder buffer = new StringBuilder();
      int pad = 0;
      for (int i = 0; i < data.length; i += 3) {

        int b = ((data[i] & 0xFF) << 16) & 0xFFFFFF;
        if (i + 1 < data.length) {
          b |= (data[i + 1] & 0xFF) << 8;
        } else {
          pad++;
        }
        if (i + 2 < data.length) {
          b |= (data[i + 2] & 0xFF);
        } else {
          pad++;
        }

        for (int j = 0; j < 4 - pad; j++) {
          int c = (b & 0xFC0000) >> 18;
          buffer.append(tbl[c]);
          b <<= 6;
        }
      }
      for (int j = 0; j < pad; j++) {
        buffer.append("=");
      }

      return buffer.toString();
    }
  }

  public static class Form {
    private Map<String, Object> data = new HashMap<String, Object>();

    public Form put(String key, Object value) {
      data.put(key, value);
      return this;
    }

    public Form put(String key, String filename, InputStream stream) {
      data.put(key, new FileEntry(filename, stream));
      return this;
    }

    private static class FileEntry {
      private InputStream stream;
      private String filename;

      public FileEntry(String filename, InputStream stream) {
        this.filename = filename;
        this.stream = stream;
      }

    }

  }

}
