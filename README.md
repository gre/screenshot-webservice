Screenshot Webservice
=====================

Screenshot Webservice is an open-source **REST web service** to perform **web page screenshots**.

**PhantomJS** is used for rendering pages.

**Play2 framework** is used for everything else:

* Caching
* Configuration (autorized formats and sizes, ...)
* Handling a multi-processing screenshot requests queue
* REST
  * GET and HEAD supported
  * HTTP Status Code for easily identifying failures
  * HTTP Headers

Installation
============

Install dependencies
--------------------

* **Play20** *HEAD* to start the application (or only java if you deploy it)
  * See https://github.com/playframework/Play20
* `phantomjs` 1.4.0(+) installed in the system
  * See http://www.phantomjs.org/

Download it
-----------
pull it or download it from Github.

Run it
------
```
$ cd screenshot-webservice/
$ ~/Play20/play                   # fix the path for your Play20 installtion
[screenshot-webservice] $ run     # (or start then C-D), you can also provide a port number
```
Now go to `http://localhost:9000/`

Configuration
=============

You can configure your instance with the `conf/application.conf` file.

You can also edit the `/` page by editing the `conf/index.html` file.

Read it for more information, every property is commented.

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

#### 200 Success

* Content-Type: image/jpg
* Expires: 23 Dec 2011 12:00:48 GMT
* Last-Modified: 23 Dec 2011 11:30:48 GMT
* Etag: 0d21b5bdca5db7fe8ae17b88f6dabb66167f2721
* Content-Length: 478349

*[image binary in the body]*

#### 403 Forbidden

something is wrong in your parameters and not supported by the server

#### 503 Service Unavailable

The server was not able to finish processing the screenshot

To avoid this problem, prefer using a HEAD request before and ensure the resource is ready.

#### 500 Internal Server Error

something goes wrong during the screenshot processing

### Example

`GET /screenshot.jpg?url=http://github.com&size=1024x1024`

Precache an URL
-----------------
You can use a **HEAD** request to trigger the screenshot processing.
The API and responses are the same as the GET API.
Instead of waiting the screenshot resource to be ready, the web service returns a 202 http code if the resource is still processing.

### Additional responses

#### 202 Accepted

The URL is being process but not yet ready

#### 200 OK

* Content-Type: image/png
* Expires: 26 Dec 2011 11:28:18 GMT
* Last-Modified: 26 Dec 2011 10:58:18 GMT
* Etag: f5b12d7e242ff0ad36e18bd842bb74b161d4cf58
* Content-Length: 478349

The URL is ready to get

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

Copyright 2011-2012 Gaëtan Renaudeau (http://gaetanrenaudeau.fr).

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this project except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
