package org.keeber.http.tests;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.keeber.http.Rest;
import org.keeber.http.Rest.Client.Request;
import org.keeber.http.Rest.Client.Response;
import org.keeber.http.Rest.RestException;

import com.google.gson.JsonObject;

public class All {
  private static final String root = "http://httpbin.org/", user = "ausername", pass = "mpassword";

  /**
   * Lets go a get.
   * 
   * @throws RestException
   */

  @Test
  public void test1() throws RestException {
    Response response = Rest.newClient(root).newRequest("status/418").get();
    assertEquals("Response code NOT 418 [" + response.getCode() + "] (probably not a teapot).", 418, response.getCode());
  }

  /**
   * Test the basic auth and the route parameters.
   * 
   * @throws RestException
   */
  @Test
  public void test2() throws RestException {
    Response response = Rest.newClient(root).basic(user, pass).newRequest("basic-auth/{user}/{pass}").get("user", user, "pass", pass);
    assertEquals("Response code NOT 200 [" + response.getCode() + "].", 200, response.getCode());
  }

  /**
   * Test a GET & HEAD.
   * 
   * @throws RestException
   */
  @Test
  public void test3() throws RestException {
    Request request = Rest.newClient(root).json().newRequest("anything");
    Response response;
    //
    response = request.get();
    assertEquals("Response code NOT 200 [" + response.getCode() + "].", 200, response.getCode());
    assertEquals("Method NOT GET.", "GET", response.<JsonObject>as(JsonObject.class).get("method").getAsString());
  }

  /**
   * POST a form.
   * 
   * @throws RestException
   */
  @Test
  public void test4() throws RestException {
    String FILE_CONTENT = "Mary had a little lamb.\nIt's fleece etc...\n";
    Response response = Rest.newClient(root).newRequest("post").post(new Rest.MultipartForm().add("param1", "value1").add("file1", "file.txt", FILE_CONTENT));
    assertEquals("Response code NOT 200 [" + response.getCode() + "].", 200, response.getCode());
    assertEquals("File content passed incorrectly.", FILE_CONTENT, response.<JsonObject>as(JsonObject.class).get("files").getAsJsonObject().get("file1").getAsString());
    assertEquals("Wrong form parameter value.", "value1", response.<JsonObject>as(JsonObject.class).get("form").getAsJsonObject().get("param1").getAsString());
  }

  /**
   * POST an XForm (some services treat them differently).
   * 
   * @throws RestException
   */
  @Test
  public void test5() throws RestException {
    Response response = Rest.newClient(root).newRequest("post").post(new Rest.XForm().add("param1", "value1").add("param2", "value2"));
    assertEquals("Wrong form parameter value.", "value1", response.<JsonObject>as(JsonObject.class).get("form").getAsJsonObject().get("param1").getAsString());
    assertEquals("Wrong form parameter value.", "value2", response.<JsonObject>as(JsonObject.class).get("form").getAsJsonObject().get("param2").getAsString());
  }


}
