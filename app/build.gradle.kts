plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
}
android {
 namespace = "com.aintcloud.proofvault"
 compileSdk = 35
 defaultConfig {
  applicationId = "com.aintcloud.proofvault"
  minSdk = 26
  targetSdk = 35
  versionCode = 9
  versionName = "9.0.0-dev"
 }
 buildTypes { release { isMinifyEnabled = false } }
 compileOptions {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
 }
 kotlinOptions { jvmTarget = "17" }
 buildFeatures { compose = true }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.documentfile:documentfile:1.0.1")
 implementation("androidx.work:work-runtime-ktx:2.10.2")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.ui:ui-tooling-preview")
 debugImplementation("androidx.compose.ui:ui-tooling")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
 testImplementation("junit:junit:4.13.2")
}
