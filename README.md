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
Rest.Client.Response response=rest.request("get").get();
if(response.hasResult()){
	...
}
```

Hint: request objects can be reused (they should be thread safe too). Also different route params can be called with each call (route params also swap into the query string too (because why not)).

```java
Rest.Client.Request request = rest.newRequest("get/{id}");
Rest.Client.Response response = request.get("id","1234322");
```

Default headers are copied from the REST instance when the request is created.

Form posts are easy, as are sending objects as the message body:

```java
Rest.Client.Response response1 = request.post(new Rest.XForm().add("param1", "myvalue"));

Rest.Client.Response response2 = request.put(new TotallyGreatObject());
```

Notice that request objects can be called with different methods.

Basic responses should be handled automatically - content types with "text" return a String, "json" returns a JsonElement, and all others return an InputStream (please remember to close it):

```java
Rest.Client.Response response = request.post(new Rest.MultipartForm().add("param1", "myvalue");
System.out.println(response.as(String.class);

Rest.Client.Response response = request.post(new Rest.XForm().add("param1", "some other value"));
response.as(InputStream.class);
```

Streaming things can be done by passing an input steam or by creating a Payload (an interface) via the io package.

```java
Rest.Client.Response response = request.post(Rest.io.newPayload(is, 209889));
```

If the lengths of all of the streams are known, so the content length can be determined - the HTTP request is sent in streaming mode (there is no local caching before sending).

# TODO

Probably change everything **again** (add more unit tests).
