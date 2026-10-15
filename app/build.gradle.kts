import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

val cloudinaryProperties = Properties().apply {
    val configFile = rootProject.file("cloudinary.properties")
    if (configFile.exists()) configFile.inputStream().use { load(it) }
}
val cloudinaryCloudName = cloudinaryProperties.getProperty("CLOUDINARY_CLOUD_NAME", "")
val cloudinaryUploadPreset = cloudinaryProperties.getProperty("CLOUDINARY_UPLOAD_PRESET", "")

// Push notification server (server/notify); empty disables notification calls.
val notifyProperties = Properties().apply {
    val configFile = rootProject.file("notify.properties")
    if (configFile.exists()) configFile.inputStream().use { load(it) }
}
val notifyUrl = notifyProperties.getProperty("NOTIFY_URL", "")

android {
    namespace = "com.example.instagaram"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.instagaram"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // These values are public by design; never put CLOUDINARY_API_SECRET in an app.
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"$cloudinaryCloudName\"")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"$cloudinaryUploadPreset\"")
        buildConfigField("String", "NOTIFY_URL", "\"$notifyUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.activity)
    // Offers "Save password?" after registering / changing the password (the fields themselves are not autofillable).
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(libs.glide)
    annotationProcessor(libs.compiler)

    implementation(libs.roundedimageview)
    implementation(libs.circleimageview)

    implementation(platform("com.google.firebase:firebase-bom:32.0.0"))
    implementation("com.google.firebase:firebase-messaging:24.0.0")
    implementation("com.google.firebase:firebase-firestore:25.0.0")

    implementation("org.mindrot:jbcrypt:0.4")
}
