if (phantom.args.length < 2) {
  console.log("Usage: phantomjs render.js url output [width height]");
  phantom.exit(1);
}

var TIMEOUT = 30000;

var url = phantom.args[0];
var output = phantom.args[1];
var width = parseInt(phantom.args[2] || 1024);
var height = parseInt(phantom.args[3] || 1024);

renderUrlToFile(url, output, width, height, function(url, file){
  console.log("Rendered '"+url+"' at size ("+width+","+height+") into '"+output+"'");
  phantom.exit(0);
});

setTimeout(function(){
  console.error("Timeout reached ("+TIMEOUT+"ms).");
  phantom.exit(2);
}, TIMEOUT);


function renderUrlToFile(url, file, width, height, callback) {
  var page = new WebPage();
  page.viewportSize = { width: width, height: height };
  page.clipRect = { top: 0, left: 0, width: width, height: height };
  page.settings.userAgent = "Phantom.js bot";

  page.open(url, function(status){
    if(status !== "success") {
      console.log("Unable to render '"+url+"' ("+status+")");
      phantom.exit(3);
    } else {
      page.render(file);
      callback(url, file);
    }
  });
}
