package org.keeber.http.tests;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.keeber.http.Rest;
import org.keeber.http.Rest.Request;
import org.keeber.http.Rest.Response;
import org.keeber.http.Rest.RestException;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class All {
  private static final String root = "http://httpbin.org/", user = "username", pass = "mpassword";

  /**
   * Lets go a get.
   * 
   * @throws RestException
   */
  @Test
  public void test1() throws RestException {
    Response<JsonElement> response = Rest.withURL(root).request("status/418").get();
    assertEquals("Response code NOT 418 [" + response.getCode() + "] (probably not a teapot).", 418, response.getCode());
  }

  /**
   * Test the basic auth and the route parameters.
   * 
   * @throws RestException
   */
  @Test
  public void test2() throws RestException {
    Response<JsonElement> response = Rest.withURL(root).basic(user, pass).request("basic-auth/{user}/{pass}").get("user", user, "pass", pass);
    assertEquals("Response code NOT 200 [" + response.getCode() + "].", 200, response.getCode());
  }

  /**
   * Test a GET & HEAD.
   * 
   * @throws RestException
   */
  @Test
  public void test3() throws RestException {
    Request request = Rest.withURL(root).json().request("anything");
    Response<JsonObject> response;
    //
    response = request.get(JsonObject.class);
    assertEquals("Response code NOT 200 [" + response.getCode() + "].", 200, response.getCode());
    assertEquals("Method NOT GET.", "GET", response.getResult().get("method").getAsString());
  }


}
