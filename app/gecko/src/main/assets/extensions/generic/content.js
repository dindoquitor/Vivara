// Repurposed for cosmetic ad filtering (element hiding).

console.log("TV Bro generic content extension loaded");

let cosmeticStyleEl = null;

// Request and inject cosmetic CSS on every page load
window.addEventListener('load', function () {
    console.log("window.load executed — requesting cosmetic CSS");
    communicatePort.postMessage({ action: "getCosmeticCSS" });
});

// Also inject immediately if the page is already loaded
if (document.readyState === 'complete' || document.readyState === 'interactive') {
    communicatePort.postMessage({ action: "getCosmeticCSS" });
}

const communicatePort = browser.runtime.connectNative("vivara_content");

communicatePort.onMessage.addListener(message => {
    console.log("Received message from native app:", message);
    if (message.action === "cosmetic" && message.data && message.data.css) {
        injectCosmeticCSS(message.data.css);
    }
});

function injectCosmeticCSS(css) {
    if (cosmeticStyleEl) {
        cosmeticStyleEl.remove();
    }
    if (css) {
        cosmeticStyleEl = document.createElement('style');
        cosmeticStyleEl.id = 'vivara-cosmetic';
        cosmeticStyleEl.textContent = css;
        (document.head || document.documentElement).appendChild(cosmeticStyleEl);
    }
}
