    var mixpanelCode = (window.location.hostname.indexOf("epiceleven") > 0)?
                        "a1889b53bda6b6348f60a570f658c157":
                        "f627312247ce937f807ce4b9d786314b";

    mixpanel.init(mixpanelCode, {api_host: "https://api.mixpanel.com"});
    mixpanel.track("Unsupported Browser");
    console.log("Unsupported Browser");
