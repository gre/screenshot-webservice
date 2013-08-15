var version = "1.1";

var           TIMEOUT = 30000,
         EXIT_SUCCESS = 0,
         EXIT_TIMEOUT = 2,
     EXIT_OPEN_FAILED = 3;


if (phantom.args.length < 2) {
  console.log("Usage: phantomjs render.js url output [width height]");
  phantom.exit(1);
}
var url = phantom.args[0];
var output = phantom.args[1];
var width = parseInt(phantom.args[2] || 1024);
var height = parseInt(phantom.args[3] || 1024);
var phantom_version = phantom.version.major+"."+phantom.version.minor+"."+phantom.version.patch;

renderUrlToFile(url, output, width, height, function(url, file){
  console.log("Rendered '"+url+"' at size ("+width+","+height+") into '"+output+"'");
  phantom.exit(EXIT_SUCCESS);
});

setTimeout(function(){
  console.error("Timeout reached ("+TIMEOUT+"ms).");
  phantom.exit(EXIT_TIMEOUT);
}, TIMEOUT);


function renderUrlToFile(url, file, width, height, callback) {
  var page = new WebPage();
  page.viewportSize = { width: width, height: height };
  page.clipRect = { top: 0, left: 0, width: width, height: height };
  page.settings.userAgent = "PhantomJS/"+phantom_version+" screenshot-webservice/"+version;

  page.open(url, function(status){
    if(status !== "success") {
      console.log("Unable to render '"+url+"' ("+status+")");
      phantom.exit(EXIT_OPEN_FAILED);
    } else {
      page.evaluate(function() {
        document.body.bgColor = 'white';
      });
      page.render(file);
      callback(url, file);
    }
  });
}
