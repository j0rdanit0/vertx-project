# vertx-project
![Build](https://github.com/j0rdanit0/vertx-project/actions/workflows/build.yml/badge.svg)

Sample RESTful HTTP web service written with the Vert.x Java API.

The basic async control flow is this:

```Router endpoint > request to Event Bus > In-memory data storage > reply to Event Bus > complete HTTP request```

All verticles are deployed from `BookRouterTest.java`. Each endpoint has its own dedicated tests, as well as a test to demonstrate them all working together.

https://www.linkedin.com/in/jordan-simpson-dev/
