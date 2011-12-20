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
* Status: 200
* Content-Type: image/png

#### Forbidden
something is wrong in your parameters and not supported by the server

* Status: 403

#### InternalServerError
something goes wrong during the screenshot processing

* Status: 500


### Example

`GET /screenshot.png?url=http://github.com&format=1024x1024`


Example
-------

![screenshot](http://i.imgur.com/rt3w6.png)


Release Note
------------

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
