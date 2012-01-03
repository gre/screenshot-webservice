var version = "1.1";
var phantom_version = phantom.version.major+"."+phantom.version.minor+"."+phantom.version.patch

var TIMEOUT = 30000;
var PORT = 9866;

if (phantom.args.length != 0 && phantom.args.length != 2) {
  console.log("Usage: phantomjs render.js [defWidth defHeight]");
  phantom.exit(1);
}
var width = parseInt(phantom.args[0] || 1024);
var height = parseInt(phantom.args[1] || 1024);

var queryString = function(a,b,c,d,e){for(b=/[?&]?([^=]+)=([^&]*)/g,c={},e=decodeURIComponent;d=b.exec(a.replace(/\+/g,' '));c[e(d[1])]=e(d[2]));return c;}

function renderUrlToFile(url, file, width, height, success, error) {
  console.log("Rendering... "+url+" in "+file+" "+width+"x"+height);
  var page = new WebPage();
  page.viewportSize = { width: width, height: height };
  page.clipRect = { top: 0, left: 0, width: width, height: height };
  page.settings.userAgent = "PhantomJS/"+phantom_version+" screenshot-webservice/"+version;
  var finished = false;

  setTimeout(function(){
    if(finished) return;
    finished = true;
    console.error("Timeout reached ("+TIMEOUT+"ms).");
    error("timeout");
  }, TIMEOUT);

  page.open(url, function(status){
    if(finished) return;
    finished = true;
    if(status !== "success") {
      console.log("Unable to render '"+url+"' ("+status+")");
      error("status", status);
    } else {
      page.evaluate(function() {
        document.body.bgColor = 'white';
      });
      page.render(file);
      console.log("Rendered '"+url+"' at size ("+width+","+height+") into '"+output+"'");
      success(url, file);
    }
  });
}

var server, service;

server = require('webserver').create();

var regexp = /^\/\?(.*)$/;

function notFound(r) {
  r.statusCode = 404;
  r.write("Not found");
}

service = server.listen(PORT, function (request, response) {
  console.debug(request.method+" "+request.url);
  var m = regexp.exec(request.url);
  if(m && m[1]) {
    var params = queryString(m[1]);
    if(params.url && params.output) {
      try {
      renderUrlToFile(params.url, params.output, params.width||width, params.height||height, function(url, file){
        response.statusCode = 200;
        response.write('{ "url": "'+url+'", "output": "'+output+'" }');
      }, function(error){
        if(error == "timeout")
        response.statusCode = 503;
        else
        response.statusCode = 500;
      response.write('{ "error": "'+error+'" }');
      });
      } catch(e) {
        console.log("Exception: "+e);
        response.statusCode = 500;
        response.write('{ "error": "'+e+'" }');
      }
    } else notFound(response);
  } else notFound(response);
});

console.log("Listening to port "+PORT+"...");

