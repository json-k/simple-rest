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

public abstract class Rest<T> {
  protected String url;
  protected Map<String, String> headers = new HashMap<String, String>();
  protected static final String AUTH = "Authorization";

  /**
   * Create a client instance with the given endpoint.
   * 
   * @param url the endpoint (typically starting with http:// or https://)
   * @return Client
   */
  public static Client newClient(String url) {
    return new Client(url);
  }

  protected Rest(String url) {
    this.url = url;
  }

  private Gson serializer;

  Gson serializer() {
    return this.serializer == null ? serializer = new GsonBuilder().setPrettyPrinting().create() : serializer;
  }

  /**
   * Set the Gson serializer for object operations.
   * 
   * @param serializer Gson instance to use.
   * @return Rest
   */
  public T serializer(Gson serializer) {
    this.serializer = serializer;
    return getThis();
  }

  public T header(String key, String value) {
    headers.put(key, value);
    return getThis();
  }

  protected abstract T getThis();

  /**
   * Removed the designated header from this client (already created requests will be unaffected).
   * 
   * @param key header key to remove eg: "User-agent"
   * @return Rest
   */
  public T clear(String key) {
    headers.remove(key);
    return getThis();
  }

  /**
   * Clear the basic authorization for this client.
   * 
   * @return Rest
   */
  public T nobasic() {
    return clear(AUTH);
  }

  /**
   * Set basic authorization for this client.
   * 
   * @param username User name.
   * @param password Password.
   * @return Rest
   */
  public T basic(String username, String password) {
    return header(AUTH, "Basic " + utils.encode(username + ":" + password));
  }

  /**
   * Sets headers for JSON communication (Accept & Content-Type).
   * 
   * @return Rest
   */
  public T json() {
    header("Accept", "application/json");
    header("Content-Type", "application/json");
    return getThis();
  }

  private enum Method {
    POST, GET, PUT, DELETE, HEAD;
  }

  public static class Client extends Rest<Client> {

    /**
     * Create a request from this client. The provided URL is appended to the clients endpoint - it
     * may also include route params. Headers from the client (including auth) will be used on
     * execution.
     * 
     * <p>
     * Requests executions (get(), post(), etc... methods) are thread safe.
     * 
     * @param url the endpoint.
     * @return Request
     */
    public Request newRequest(String url) {
      return new Request(url);
    }

    /**
     * Create a request from this client. Using the base URL for the client. Headers from the client
     * (including auth) will be used on execution.
     * 
     * <p>
     * Requests executions (get(), post(), etc... methods) are thread safe.
     * 
     * @param url the endpoint.
     * @return Request
     */
    public Request newRequest() {
      return new Request("");
    }

    private Client(String url) {
      super(url);
    }

    public class Request extends Rest<Request> {
      private Request(String url) {
        super(url);
      }

      private String query;

      @Override
      protected Request getThis() {
        return this;
      }

      /**
       * Add query parameters to this request - this method will perform URL encoding.
       * 
       * <p>
       * Query parameters are cumulative (ie: they do not use an underlying map) - however them may
       * contain (or consist entirely of) route parameters in the form of {myparam}.
       * 
       * @param name of the parameter
       * @param value of the parameter
       * @return Request
       */
      public Request query(String name, String value) {
        try {
          query = (query == null ? "?" : query + "&") + name + "=" + URLEncoder.encode(value, utils.UTF_8);
        } catch (UnsupportedEncodingException e) {
          throw new RuntimeException(e.getMessage(), e);
        }
        return this;
      }

      /**
       * Execute a GET transformed to the expected type with the specified route params.
       * 
       * <p>
       * To replace the route param {id} in the URL (or query string) the route params
       * "id","myidvalue" would be provided.
       * 
       * @param type expected return type or class.
       * @param routes optional string list of routes.
       * @return Response
       * @throws RestException
       */
      public <R> Response<R> get(Type type, String... routes) throws RestException {
        return execute(this, null, Method.GET, type, routes);
      }

      /**
       * Execute a GET as a JsonElement.
       * 
       * @param routes optional string list of routes.
       * @return Response
       * @throws RestException
       */
      public Response<JsonElement> get(String... routes) throws RestException {
        return get(JsonElement.class, routes);
      }

      /**
       * Stream a GET.
       * 
       * @param routes optional string list of routes.
       * @return Response
       * @throws RestException
       */
      public Response<InputStream> stream(String... routes) throws RestException {
        return get(InputStream.class, routes);
      }

      /**
       * Execute a HEAD transformed to the expected type with the specified route params.
       * 
       * @param type expected return type or class.
       * @param routes optional string list of routes.
       * @return Response
       * @throws RestException
       */
      public <R> Response<R> head(Type type, String... routes) throws RestException {
        return execute(this, null, Method.HEAD, type, routes);
      }

      /**
       * Execute a HEAD as a JsonElement.
       * 
       * @param routes optional string list of routes.
       * @return Response
       * @throws RestException
       */
      public Response<JsonElement> head(String... routes) throws RestException {
        return head(JsonElement.class, routes);
      }

      /**
       * Execute a POST with the provided object as the body transformed to the type with the
       * optional route params.
       * 
       * @param body for the post - can be a Rest.Form object for a multi-part post.
       * @param type expected return type or class.
       * @param routes optional string list of routes.
       * @return Response
       * @throws RestException
       */
      public <R> Response<R> post(Object body, Type type, String... routes) throws RestException {
        return execute(this, body, Method.POST, type, routes);
      }

      /**
       * Execute a POST as a JSONElement.
       * 
       * @param body for the post - can be a Rest.Form object for a multi-part post.
       * @param routes optional string list of routes.
       * @return Response
       * @throws RestException
       */
      public Response<JsonElement> post(Object body, String... routes) throws RestException {
        return post(body, JsonElement.class, routes);
      }

      /**
       * Execute a PUT with the provided object as the body transformed to the type with the
       * optional route params.
       * 
       * @param body for the post - can be a Rest.Form object for a multi-part post.
       * @param type expected return type or class.
       * @param routes optional string list of routes.
       * @return Response
       * @throws RestException
       */
      public <R> Response<R> put(Object body, Type type, String... routes) throws RestException {
        return execute(this, body, Method.PUT, type, routes);
      }

      /**
       * Execute a PUT as a JSONElement.
       * 
       * @param body for the post - can be a Rest.Form object for a multi-part post.
       * @param routes optional string list of routes.
       * @return Response
       * @throws RestException
       */
      public Response<JsonElement> put(Object body, String... routes) throws RestException {
        return put(body, JsonElement.class, routes);
      }

      /**
       * Execute a DELETE as transformed into the provided type.
       * 
       * @param type expected return type or class.
       * @param routes optional string list of routes.
       * @return Response
       * @throws RestException
       */
      public <R> Response<R> delete(Type type, String... routes) throws RestException {
        return execute(this, null, Method.DELETE, type, routes);
      }

      /**
       * Execute a DELETE as a JSONElement.
       * 
       * @param routes
       * @param routes optional string list of routes.
       * @return Response
       * @throws RestException
       */
      public Response<JsonElement> delete(String... routes) throws RestException {
        return delete(JsonElement.class, routes);
      }

    }

    /**
     * An HTTP response class.
     * 
     * @author Jason Keeber <jason@keeber.org>
     *
     * @param <T>
     */
    public static class Response<R> {
      private R result;
      private int code, length;
      private String message;

      /**
       * Does this response contain a non-null value for type T?
       * 
       * @return true is the result is present.
       */
      public boolean hasResult() {
        return result != null;
      }

      /**
       * The payload of the response.
       * 
       * @return result of type T.
       */
      public R getResult() {
        return result;
      }

      /**
       * The code of the response ie: 200 == OK
       * 
       * @return HTTP response code.
       */
      public int getCode() {
        return code;
      }

      /**
       * The contents length (if available).
       * 
       * @return the content length.
       */
      public int getLength() {
        return length;
      }

      /**
       * The response message.
       * 
       * @return eg: OK
       */
      public String getMessage() {
        return message;
      }
    }

    @Override
    protected Client getThis() {
      return this;
    }

    @SuppressWarnings("unchecked")
    protected <R> Response<R> execute(Request request, Object body, Method method, Type type, String... routes) throws RestException {
      Response<R> response = new Response<R>();
      HttpURLConnection connection = null;
      boolean streaming = type.equals(InputStream.class), multipart = body != null && body instanceof Form;
      try {
        // Swap the route params into the url
        String furl = new StringBuilder(url).append(request.url).append(request.query == null ? "" : request.query).toString();
        if (routes.length % 2 != 0) {
          throw new RestException("Even number of route paramaters expected [" + routes.length + "]");
        }
        for (int i = 0; i < routes.length; i += 2) {
          furl = furl.replaceAll("(%7B|\\{)" + routes[i] + "(%7D|\\})", URLEncoder.encode(routes[i + 1], utils.UTF_8));
        }
        // Create the connection & set the method
        connection = (HttpURLConnection) new URL(furl).openConnection();
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
                wt.append(entry.getValue() instanceof String ? entry.getValue() + "" : request.serializer().toJson(entry.getValue()));
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
              is = new ByteArrayInputStream((body instanceof String ? (String) body : request.serializer().toJson(body)).getBytes(utils.UTF_8));
            }
            utils.copy(is, connection.getOutputStream(), true);
          }
        }
        response.code = connection.getResponseCode();
        response.message = connection.getResponseMessage();
        response.length = streaming ? connection.getContentLength() : -1;
        if (response.code >= 200 && response.code < 400) {
          if (streaming) {
            response.result = (R) new AutocloseConnectionStream(connection, connection.getInputStream());
          } else {
            String result = utils.asString(connection.getInputStream());
            response.result = (R) (type.equals(String.class) ? result : request.serializer().fromJson(result, type));
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

  }



  /**
   * A stream that disconnection the Http URL Connection when it is closed.
   * 
   * @author Jason Keeber <jason@keeber.org>
   *
   */
  private static class AutocloseConnectionStream extends FilterInputStream {
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

  /**
   * A HTTP Form representation.
   * 
   * @author Jason Keeber <jason@keeber.org>
   *
   */
  public static class Form {
    private Map<String, Object> data = new HashMap<String, Object>();

    /**
     * Add the object to this form with the provided key. None-string objects will be serialized
     * (using the Rest client serializer) to JSON objects when the form is POSTed.
     * 
     * @param key the form name.
     * @param value the form value.
     * @return Form
     */
    public Form put(String key, Object value) {
      data.put(key, value);
      return this;
    }

    /**
     * Add the provided stream as a file to the Form.
     * 
     * @param key the form name.
     * @param filename the file upload name.
     * @param stream the file content.
     * @return Form
     */
    public Form put(String key, String filename, InputStream stream) {
      data.put(key, new FileEntry(filename, stream));
      return this;
    }

    /**
     * Add the provided string content as a file upload to the Form.
     * 
     * @param key the form name.
     * @param filename the file upload name.
     * @param filecontent string file content.
     * @return Form
     */
    public Form put(String key, String filename, String filecontent) {
      try {
        data.put(key, new FileEntry(filename, new ByteArrayInputStream(filecontent.getBytes(utils.UTF_8))));
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
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


