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

`GET /screenshot.{format}`

### Input

* `format` *Required (url)* : **string** (example: jpg, png, ...) as defined in the conf
* `url` *Required* : **string**
* `size` *Optional* : ***{width}*x*{height}*** (example: 1024x1024) as defined in the conf

### Response

#### Success

HTTP/1.1 **200 Success**

* Content-Type: image/jpg
* Expires: 23 Dec 2011 12:00:48 GMT
* Last-Modified: 23 Dec 2011 11:30:48 GMT
* Etag: 0d21b5bdca5db7fe8ae17b88f6dabb66167f2721
* Content-Length: 478349

*[image binary in the body]*

#### Forbidden

something is wrong in your parameters and not supported by the server

HTTP/1.1 **403 Forbidden**

#### Service Unavailable

The server was not able to finish processing the screenshot

HTTP/1.1 **503 Service Unavailable**

To avoid this problem, prefer using a HEAD request before and ensure the resource is ready.


#### Internal Server Error

something goes wrong during the screenshot processing

HTTP/1.1 **500 Internal Server Error**

### Example

`GET /screenshot.jpg?url=http://github.com&size=1024x1024`

Precache an URL
-----------------
You can use a **HEAD** request to trigger the screenshot processing.
The API is the same as the GET API.

### Additional responses

#### The URL is being process but not yet ready

HTTP/1.1 **202 Accepted**

#### The URL is ready to get

HTTP/1.1 **200 OK**

* Content-Type: image/png
* Expires: 26 Dec 2011 11:28:18 GMT
* Last-Modified: 26 Dec 2011 10:58:18 GMT
* Etag: f5b12d7e242ff0ad36e18bd842bb74b161d4cf58
* Content-Length: 478349

Example
-------

![screenshot](http://i.imgur.com/rt3w6.png)


Release Note
------------

### v1.1.1
  * add configurable format
  * perf improvments

### v1.1
  * add more Http responses and headers
  * add HEAD request for pinging the cache
  * clean old cache files
  * using Actor for queue of screenshot
  * don't queue if a same request is waiting (but bind to it)

### v1.0.1
  * replaced the width and height by size
  * add autorized sizes
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
