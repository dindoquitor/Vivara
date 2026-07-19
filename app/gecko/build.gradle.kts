plugins {
    id("vivara.android.library")
}

android {
    namespace = "com.vivara.browser.webengine.gecko"
}

dependencies {
    implementation(project(":app:common"))
    implementation(libs.androidx.appcompat)
    implementation(libs.geckoview)
    testImplementation(libs.junit)
}
