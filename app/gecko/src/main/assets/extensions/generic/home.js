console.log("TV Bro home content extension loaded");

const homeExtPort = browser.runtime.connectNative("vivara");
function postMessageToHomePagePort(action, data) {
    //console.log("Sending message to native app: " + action);
    homeExtPort.postMessage({ action: action, data: data });
}

let Vivara = {
    startVoiceSearch: function () {
        postMessageToHomePagePort("startVoiceSearch");
    },
    setSearchEngine: function (engine, customSearchEngineURL) {
        postMessageToHomePagePort("setSearchEngine", { engine: engine, customSearchEngineURL: customSearchEngineURL });
    },
    onEditBookmark: function (bookmark) {
        postMessageToHomePagePort("onEditBookmark", bookmark);
    },
    onHomePageLoaded: function () {
        postMessageToHomePagePort("onHomePageLoaded");
    },
    requestFavicon: function (url) {
        postMessageToHomePagePort("requestFavicon", url);
    },
    markBookmarkRecommendationAsUseful: function (bookmarkIndex) {
        postMessageToHomePagePort("markBookmarkRecommendationAsUseful", bookmarkIndex);
    }
}
window.wrappedJSObject.Vivara = cloneInto(
    Vivara,
    window,
    { cloneFunctions: true });

homeExtPort.onMessage.addListener(message => {
    switch (message.action) {
        case "favicon": {
            let favicon = message.data;
            if (window.wrappedJSObject.onFaviconLoaded) {
                window.wrappedJSObject.onFaviconLoaded(favicon.url, favicon.data);
            }
        }
    }
});