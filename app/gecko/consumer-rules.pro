-keep class com.vivara.browser.webengine.gecko.GeckoWebEngine { *; }

-keepclassmembers class org.mozilla.geckoview.** {
    *** mDisplay;
}
