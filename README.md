Screenshot Webservice
=====================

Screenshot Webservice is an open-source **REST web service** to perform **web page screenshots**.

It use **PhantomJS** for rendering behind a **Play2 framework** application for handling caching.

Requirement
-----------

* **Play20** *HEAD* to start the application (or only java if you deploy it)
* `phantomjs` installed in the system


The API
=======

Screenshot an URL
-----------------

`GET /screenshot.png`

### Input

* `url` *Required* : **string**
* `format` *Optional* : ***{width}*x*{height}*** (example: 1024x1024)

### Response

#### Success
* Status: 200 Success
* Content-Type: image/png

#### Forbidden
something is wrong in your parameters and not supported by the server

* Status: 403 Forbidden

#### The server was not able to finish processing the screenshot
* Status: 503 Service Unavailable

#### Internal Server Error
something goes wrong during the screenshot processing

* Status: 500 Internal Server Error


### Example

`GET /screenshot.png?url=http://github.com&format=1024x1024`

Precache an URL
-----------------
You can use a **HEAD** request to trigger the screenshot processing.
The API is the same as the GET API.

There is one new HTTP Status:

### The URL is being process but not yet ready
* Status: 202 Accepted

### The URL is ready to get
* Status: 200 Ok


Example
-------

![screenshot](http://i.imgur.com/rt3w6.png)


Release Note
------------

### v1.1
  * add more Http responses
  * add HEAD request for pinging the cache
  * clean old cache files
  * using Actor for queue of screenshot
  * don't queue if a same request is waiting (but bind to it)

### v1.0.1
  * replaced the width and height by format
  * add autorized formats
  * better way to forbid local addresses
  * configurable expiration

### v1.0
  * basic version with url, width and height params

Licence
=======

This software is licensed under the Apache 2 license, quoted below.

Copyright 2011 Gaëtan Renaudeau (http://gaetanrenaudeau.fr).

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this project except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
