![Simple Rest](http://keeber.org/wp-content/uploads/2016/03/simple-rest.png)

Java REST client (simple)

## Philosophy

It seems like REST interfaces are everywhere, along with simple CuRL examples posting JSON content. It should be easy right?

Then I find myself in Java - adding a special repository, downloading a client to talk to the interface, creating objects, learning factories, translating from JSON to Java and back again. Then...just when everything looks fine I see an option I need missing from the Java client. It's right there in the CuRL examples...

This is a simple, one class, Java 1.6, thread safe, REST (like) client that uses Gson for serialization (because it's fab). That's it!

# Maven

The project is available in the Maven Central Repository. For your Gradle build:

```
	compile 'org.keeber:simple-rest:+'
```

# Quickstart

Create a client:

```java
Rest.Client rest=Rest.newClient("http://httpbin.org/").header("User-Agent", "JSON-K");
```

Clients are creataed using a base URL - handy for a fixed endpoint.

Make a request and call get: the endpoint is the base plus request URL (in this case "http://httpbin.org/get").

```java
Rest.Client.Response<JsonElement> response=rest.request("get").get();
if(response.hasResult()){
	...
}
```

Hint: request objects can be reused (they should be thread safe too). Also different route params can be called with each call (route params also swap into the query string too (because why not)).

```java
Rest.Client.Request request = rest.newRequest("get/{id}");
Rest.Client.Response<JsonElement> response = request.get("id","1234322");
```

Default headers are copied from the REST instance when the request is created.

Form posts are easy, as are sending objects as the message body:

```java
Rest.Client.Response<JsonElement> response1 = request.post(new Rest.Form().put("param1", "myvalue"));

Rest.Client.Response<TotallyGreatResponse> response2 = request.put(new TotallyGreatObject(),TotallyGreatResponse.class);
```

Notice that request objects can be called with different methods.

Don't want a JSON response - ask for something else (if you ask for a Stream you can stream the response):

```java
Rest.Client.Response<String> response = request.post(new Rest.Form().put("param1", "myvalue"), String.class);

Rest.Client.Response<InputStream> response = request.post(new Rest.Form().put("param1", "some other value"), InputStream.class);
```

# TODO

Probably change everything again (add more unit tests).
