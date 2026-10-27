plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.shamsisaz.jalali"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.shamsisaz.jalali"
        minSdk = 24
        targetSdk = 34
        versionCode = 5
        versionName = "2.1.0"
    }

    // کلید امضای ثابت و عمومی (اپ متن‌باز و نصب دستی است): همهٔ APKهای ساخته‌شده — از هر کامپیوتر یا CI —
    // یک امضا دارند و روی هم آپدیت می‌شوند. برای کلید شخصی، متغیرهای SIGNING_* را در محیط بگذارید.
    signingConfigs {
        create("shared") {
            storeFile = file(System.getenv("SIGNING_STORE_FILE") ?: "${rootDir}/keystore/shamsisaz.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: "shamsisaz-open"
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "shamsisaz"
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: "shamsisaz-open"
        }
    }

    buildTypes {
        release {
            // R8 خاموش است: شیزوکو و Hidden API با رفلکشن کار می‌کنند و حذف/تغییر نام کلاس‌ها آن‌ها را می‌شکند
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // Shizuku binder اول با نسخه قدیمی‌تر سازگار است
    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
        }
    }
    buildFeatures {
        viewBinding = false
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
    lint {
        // آیکون‌ها و نسخهٔ وابستگی‌ها هشدار می‌دهند ولی خطای واقعی نیستند
        disable += setOf("IconLauncherShape", "GradleDependency", "AppBundleLocaleChanges", "BatteryLife")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // شیزوکو — «ارواح فارسی»: دسترسی ADB از داخل خود گوشی، بدون روت
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    // دور زدن Hidden API برای تغییر زبان سیستم (فقط با دسترسی شیزوکو/ADB کار می‌کند)
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.5.0")
    // مرجع مستقل برای مقایسهٔ تبدیل تاریخ‌ها در تست
    testImplementation("com.ibm.icu:icu4j:74.2")
}
