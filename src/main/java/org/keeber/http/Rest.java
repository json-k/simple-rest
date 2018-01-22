package org.keeber.http;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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
    return header(AUTH, "Basic " + io.encode(username + ":" + password));
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
          query = (query == null ? "?" : query + "&") + name + "=" + URLEncoder.encode(value, io.UTF_8);
        } catch (UnsupportedEncodingException e) {
          throw new RuntimeException(e.getMessage(), e);
        }
        return this;
      }

      /**
       * Execute a GET with the specified route params.
       * 
       * <p>
       * To replace the route param {id} in the URL (or query string) the route params
       * "id","myidvalue" would be provided.
       * 
       * @param routes optional string list of routes.
       * @return Response
       * @throws RestException
       */
      public Response get(String... routes) throws RestException {
        return execute(this, null, Method.GET, routes);
      }

      /**
       * Execute a HEAD with the specified route params.
       * 
       * @param routes optional string list of routes.
       * @return Response
       * @throws RestException
       */
      public Response head(String... routes) throws RestException {
        return execute(this, null, Method.HEAD, routes);
      }


      /**
       * Execute a POST with the provided object as the body with the optional route params.
       * 
       * @param body for the post - can be a Rest.Form object for a multi-part post.
       * @param routes optional string list of routes.
       * @return Response
       * @throws RestException
       */
      public Response post(Object body, String... routes) throws RestException {
        return execute(this, body, Method.POST, routes);
      }


      /**
       * Execute a PUT with the provided object as the body with the optional route params.
       * 
       * @param body for the post - can be a Rest.Form object for a multi-part post.
       * @param type expected return type or class.
       * @param routes optional string list of routes.
       * @return Response
       * @throws RestException
       */
      public Response put(Object body, String... routes) throws RestException {
        return execute(this, body, Method.PUT, routes);
      }


      /**
       * Execute a DELETE with the optional route params.
       * 
       * @param type expected return type or class.
       * @param routes optional string list of routes.
       * @return Response
       * @throws RestException
       */
      public Response delete(String... routes) throws RestException {
        return execute(this, null, Method.DELETE, routes);
      }

    }

    /**
     * An HTTP response class.
     * 
     * @author Jason Keeber <jason@keeber.org>
     *
     * @param <T>
     */
    public class Response {
      private Object result;
      private int code, length;
      private String message, contentType;

      /**
       * Does this response contain a non-null value for type T?
       * 
       * @return true is the result is present.
       */
      public boolean hasResult() {
        return result != null;
      }

      public JsonObject asJsonObject() throws RestException {
        return as(JsonObject.class);
      }

      public String asString() throws RestException {
        return as(String.class);
      }

      public InputStream asStream() throws RestException {
        return as(InputStream.class);
      }

      /**
       * Converts the result (if present) to the requested type (if necessary). Possible types are
       * String (for text responses), JsonElement (for json content types), and InputStream (for all
       * other types).
       * 
       * <p>
       * JsonElement types can be converted to other types as appropriate.
       * 
       * @param type
       * @return
       * @throws RestException
       */
      @SuppressWarnings("unchecked")
      public <T> T as(Type type) throws RestException {
        if (result == null) {
          return null;
        }
        if (result instanceof InputStream) {
          if (type.equals(InputStream.class)) {
            return (T) result;
          }
          throw new RestException("Cannot convert streaming response type [" + type.getTypeName() + "]");
        }
        if (result instanceof JsonElement) {
          if (type.equals(String.class)) {
            return (T) serializer().toJson(result);
          }
          if (type.equals(JsonArray.class)) {
            return (T) ((JsonElement) result).getAsJsonArray();
          }
          if (type.equals(JsonObject.class)) {
            return (T) ((JsonElement) result).getAsJsonObject();
          }
          if (type.equals(JsonElement.class)) {
            return (T) result;
          }
          return serializer().fromJson((JsonElement) result, type);
        } else {
          if (type.equals(String.class)) {
            return (T) result.toString();
          }
          return serializer().fromJson(result.toString(), type);
        }
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

      /**
       * The content type of the response.
       * 
       * @return
       */
      public String getContentType() {
        return contentType;
      }



    }

    @Override
    protected Client getThis() {
      return this;
    }

    protected Response execute(Request request, Object body, Method method, String... routes) throws RestException {
      Response response = new Response();
      HttpURLConnection connection = null;
      Payload payload = null;
      if (body != null) {
        if (body instanceof Payload) {
          payload = (Payload) body;
        } else if (body instanceof InputStream) {
          payload = io.newPayload((InputStream) body);
        } else if (body instanceof String) {
          payload = io.newPayload((String) body);
        } else {
          payload = io.newPayload(request.serializer().toJson(body), "application/json");
        }
      }
      boolean streaming = false;
      try {
        // Swap the route params into the url
        String furl = new StringBuilder(url).append(request.url).append(request.query == null ? "" : request.query).toString();
        if (routes.length % 2 != 0) {
          throw new RestException("Even number of route paramaters expected [" + routes.length + "]");
        }
        for (int i = 0; i < routes.length; i += 2) {
          furl = furl.replaceAll("(%7B|\\{)" + routes[i] + "(%7D|\\})", URLEncoder.encode(routes[i + 1], io.UTF_8));
        }
        // Create the connection & set the method
        connection = (HttpURLConnection) new URL(furl).openConnection();
        connection.setRequestMethod(method.toString());
        // Set the content type - which might be overridden by one of the headers.
        if (payload != null) {
          connection.setRequestProperty("Content-Type", payload.getContentType());
        }
        // Set the headers
        {
          for (Entry<String, String> header : headers.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
          }
          for (Entry<String, String> header : request.headers.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
          }
          // If the content is streamable we set stream mode.
          if (payload != null && (payload.isStreamable() && payload.getLength() > 0)) {
            connection.setFixedLengthStreamingMode(payload.getLength());
            connection.setRequestProperty("Content-length", payload.getLength() + "");
          }
        }
        connection.setDoInput(true);
        connection.setDoOutput(body != null);
        connection.connect();
        // Request BODY
        if (payload != null) {
          payload.write(new BufferedOutputStream(connection.getOutputStream()));
        }
        response.code = connection.getResponseCode();
        response.message = connection.getResponseMessage();
        response.length = connection.getContentLength();
        response.contentType = connection.getContentType();
        if (response.code >= 200 && response.code < 400) {
          if (response.contentType.matches(".*(text|json).*")) {
            String result = io.asString(connection.getInputStream());
            response.result = (response.contentType.contains("text") ? result : request.serializer().fromJson(result, JsonElement.class));
          } else {
            response.result = new AutocloseConnectionStream(connection, connection.getInputStream());
            streaming = true;
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
   * IO utility class.
   * 
   * @author Jason Keeber <jason@keeber.org>
   *
   */
  public static class io {
    private static class StringPayload implements Payload {
      private String content;
      private String contentType = "text/plain";

      private StringPayload(String content) {
        this.content = content;
      }

      private StringPayload(String content, String contentType) {
        this.content = content;
        this.contentType = contentType;
      }

      @Override
      public boolean isStreamable() {
        return true;
      }

      @Override
      public long getLength() {
        return content.getBytes().length;
      }

      @Override
      public void write(OutputStream os) throws IOException {
        io.copy(new ByteArrayInputStream(content.getBytes()), os, true);
      }

      @Override
      public String getContentType() {
        return contentType;
      }

    }

    private static class InputStreamPayload implements Payload {
      private InputStream is;
      private String contentType = "application/octet-stream";
      private long length = -1;

      private InputStreamPayload(InputStream is) {
        this.is = is;
      }

      private InputStreamPayload(InputStream is, long length) {
        this.is = is;
        this.length = length;
      }

      private InputStreamPayload(InputStream is, long length, String contentType) {
        this.is = is;
        this.length = length;
        this.contentType = contentType;
      }

      private InputStreamPayload(InputStream is, String contentType) {
        this.is = is;
        this.contentType = contentType;
      }

      @Override
      public boolean isStreamable() {
        return false;
      }

      @Override
      public long getLength() {
        return length;
      }

      @Override
      public void write(OutputStream os) throws IOException {
        io.copy(is, os, true);
      }

      @Override
      public String getContentType() {
        return contentType;
      }

    }

    public static Payload newPayload(String content, String contentType) {
      return new StringPayload(content, contentType);
    }

    public static Payload newPayload(String content) {
      return new StringPayload(content);
    }

    public static Payload newPayload(InputStream is, long length, String contentType) {
      return new InputStreamPayload(is, length, contentType);
    }

    public static Payload newPayload(InputStream is, long length) {
      return new InputStreamPayload(is, length);
    }

    public static Payload newPayload(InputStream is, String contentType) {
      return new InputStreamPayload(is, contentType);
    }

    public static Payload newPayload(InputStream is) {
      return new InputStreamPayload(is);
    }

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
          io.close(is);
          io.close(os);
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

      StringBuffer buffer = new StringBuffer();
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
  public static class MultipartForm implements Payload {
    private static class Part {
      private InputStream is;
      private long length;

      private Part(String content) {
        try {
          byte[] bytes = content.getBytes(io.UTF_8);
          this.length = bytes.length;
          this.is = new ByteArrayInputStream(bytes);
        } catch (UnsupportedEncodingException e) {
          //
        }
      }

      private Part(InputStream is, long length) {
        this.is = is;
        this.length = length;
      }

    }

    private String boundary = "AEX0908096763745435x0";
    private String tail = "--" + boundary + "--" + io.LF;
    private boolean streamable = true;
    private long length = 0;

    private List<Part> parts = new LinkedList<Part>();

    private MultipartForm add(Part part) {
      if (part.length < 0) {
        streamable = false;
      }
      length = length + part.length;
      parts.add(part);
      return this;
    }

    /**
     * Add the object to this form with the provided key. None-string objects will be serialized
     * (using the Rest client serializer) to JSON objects when the form is POSTed.
     * 
     * @param key the form name.
     * @param value the form value.
     * @return Form
     */
    public MultipartForm add(String key, String value) {
      StringBuffer b = new StringBuffer();
      b.append("--" + boundary).append(io.LF);
      b.append("Content-Disposition: form-data; name=\"").append(key).append("\"").append(io.LF);
      b.append("Content-Type: text/plain; charset=").append(io.UTF_8).append(io.LF).append(io.LF);
      try {
        b.append(URLEncoder.encode(value, io.UTF_8));
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
      b.append(io.LF);
      return add(new Part(b.toString()));
    }

    public MultipartForm add(String key, String filename, InputStream stream) {
      return add(key, filename, stream, -1);
    }

    /**
     * Add the provided stream as a file to the Form. There is another method without the length if
     * it is unknown - BUT it must be present to enable true streaming.
     * 
     * @param key the form name.
     * @param filename the file upload name.
     * @param stream the file content.
     * @param length of the file content.
     * @return Form
     */
    public MultipartForm add(String key, String filename, InputStream stream, long length) {
      StringBuffer b = new StringBuffer();
      b.append("--" + boundary).append(io.LF);
      b.append("Content-Disposition: form-data; name=\"").append(key).append("\"; filename=\"");
      try {
        b.append(URLEncoder.encode(filename, io.UTF_8)).append("\"").append(io.LF);
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
      b.append("Content-Type: ").append(HttpURLConnection.guessContentTypeFromName(filename)).append(io.LF);
      b.append("Content-Transfer-Encoding: binary").append(io.LF).append(io.LF);
      add(new Part(b.toString()));
      add(new Part(stream, length));
      return add(new Part(io.LF + ""));
    }

    /**
     * Add the provided string content as a file upload to the Form.
     * 
     * @param key the form name.
     * @param filename the file upload name.
     * @param filecontent string file content.
     * @return Form
     */
    public MultipartForm add(String key, String filename, String content) {
      try {
        byte[] file = content.getBytes(io.UTF_8);
        return add(key, filename, new ByteArrayInputStream(file), file.length);
      } catch (UnsupportedEncodingException e) {
        return null;
      }
    }


    @Override
    public boolean isStreamable() {
      return streamable;
    }


    @Override
    public long getLength() {
      try {
        return length + tail.getBytes(io.UTF_8).length;
      } catch (UnsupportedEncodingException e) {
        return 0;
      }
    }


    @Override
    public void write(OutputStream os) throws IOException {
      for (Part part : parts) {
        io.copy(part.is, os, false);
      }
      os.write(tail.getBytes(io.UTF_8));
      os.flush();
      os.close();
    }

    @Override
    public String getContentType() {
      return "multipart/form-data; boundary=" + boundary;
    }

  }

  public static class XForm implements Payload {
    private String data = "";

    public XForm add(String key, String value) {
      try {
        data = (data.length() > 0 ? data + "&" : "") + key + "=" + URLEncoder.encode(value, io.UTF_8);
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e.getMessage(), e);
      }
      return this;
    }

    @Override
    public long getLength() {
      try {
        return data.getBytes(io.UTF_8).length;
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void write(OutputStream os) throws IOException {
      io.copy(new ByteArrayInputStream(data.getBytes(io.UTF_8)), os, true);
    }

    @Override
    public String getContentType() {
      return "application/x-www-form-urlencoded";
    }

    @Override
    public boolean isStreamable() {
      return true;
    }

  }

  /**
   * A basic payload for HTTP post / put methods - really used to determine content length to turn
   * on actual streaming (ie: not cached).
   * 
   * @author Jason Keeber <jason@keeber.org>
   *
   */
  private static interface Payload {

    public boolean isStreamable();

    public long getLength();

    public void write(OutputStream os) throws IOException;

    public String getContentType();

  }

}


