var parser = new UAParser();

function isValidBrowser(userAgentDetected) {
    var valid = true;

    switch (userAgentDetected.browser.name) {
        case "IE":
            valid = parseInt(userAgentDetected.browser.version) >= 9;
            break;

        case "Firefox":
            valid = parseInt(userAgentDetected.browser.version) >= 16;
            break;

        case "Chrome":
            valid = parseInt(userAgentDetected.browser.version) >= 27;
            break;

        case "Safari":
            valid = parseInt(userAgentDetected.browser.version) >= 7;
            break;

        case "Opera":
            valid = parseInt(userAgentDetected.browser.version) >= 12;
            break;
    }

    return valid;
}

if (!isValidBrowser(parser.getResult())) {
    window.location = "/unsupported.html";
}
