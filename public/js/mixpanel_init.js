    
    var isProdEnv = (function() {
      var urlContains = function(str) { return window.location.href.indexOf(str) > 0; },
          isLocalHost = urlContains("127.") || urlContains("localhost"),
          isForcedProd = urlContains("prod=true"),
          isNgrok = urlContains("ngrok"),
          isStaging = urlContains("staging"),
          isDev = (isNgrok || isLocalHost || isStaging) && !isForcedProd;
      
      if (isDev) {
        console.log("isLocalhost: " + isLocalHost + 
                    " | isForcedProd: " + isForcedProd + 
                    " | isNgrok: " + isNgrok + 
                    " | isStaging: " + isStaging + 
                    " | isDev: " + isDev);
      }
      
      return !isDev;
    })();
    
    var mixpanelCode = isProdEnv === true ? "a1889b53bda6b6348f60a570f658c157": "f627312247ce937f807ce4b9d786314b";

    mixpanel.init(mixpanelCode, {
        api_host: "https://api.mixpanel.com",
        'loaded': function() {
          mixpanel.track("Load WebApp Started");
          console.log("Load WebApp Started");
        }
      });
    

/*
    if (typeof mixpanel !== "undefined") {
        mixpanel.init(mixpanelCode);
        mixpanel.track("Landing Page Init");
    }
*/
