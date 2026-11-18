plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.example.simregionswitcher"; compileSdk = 35
 defaultConfig { applicationId = "com.example.simregionswitcher.v2"; minSdk = 33; targetSdk = 35; versionCode = 92; versionName = "2.65-t2" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildFeatures { aidl = true }
}




tasks.register<JavaExec>("coreChecks") {
    dependsOn("compileDebugUnitTestKotlin")
    mainClass.set("CoreChecksKt")
    systemProperty("local.apn.fixture", layout.projectDirectory.file("src/test/resources/apn-state-v16-device.txt").asFile.absolutePath)
    systemProperty("local.t2.ru.fixture", layout.projectDirectory.file("src/main/assets/t2_ru.pb").asFile.absolutePath)
    systemProperty("local.megafon.ru.fixture", layout.projectDirectory.file("src/main/assets/megafon_ru.pb").asFile.absolutePath)
    systemProperty("local.beeline.ru.fixture", layout.projectDirectory.file("src/main/assets/beeline_ru.pb").asFile.absolutePath)
    systemProperty("local.mts.ru.fixture", layout.projectDirectory.file("src/main/assets/mts_ru.pb").asFile.absolutePath)
    systemProperty("local.mts.fixture", layout.projectDirectory.file("src/main/assets/mts_by.pb").asFile.absolutePath)
    systemProperty("local.pb.fixture", layout.projectDirectory.file("src/main/assets/a1_by.pb").asFile.absolutePath)
    classpath = files(
        layout.buildDirectory.dir("tmp/kotlin-classes/debugUnitTest"),
        layout.buildDirectory.dir("tmp/kotlin-classes/debug")
    ) + configurations.getByName("debugUnitTestRuntimeClasspath")
}

dependencies {
    implementation(files("libs/hiddenapibypass-6.1.aar"))
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}





