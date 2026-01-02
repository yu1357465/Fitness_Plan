plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.fitness_plan"

    // 修正 1: 标准的 compileSdk 赋值方式
    compileSdk = 34
    // 注意：如果你确实安装了 API 35 或 36，可以改成对应数字。
    // 但 'version = release(36)' 这种写法是不对的。

    defaultConfig {
        applicationId = "com.example.fitness_plan"
        minSdk = 24

        // 修正 2: targetSdk 也直接赋值
        targetSdk = 34

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // 修正 3: Kotlin DSL 中必须使用括号 ()
    // Room 运行库
    implementation(libs.androidx.room.runtime)

    // 修正 4: 注解处理器也必须加括号
    // Room 注解处理器 (Java 项目必须使用 annotationProcessor，Kotlin 项目则用 ksp)
    annotationProcessor(libs.androidx.room.compiler)

    // 【正确位置】MPAndroidChart 图表库
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    implementation("com.google.code.gson:gson:2.10.1")

}