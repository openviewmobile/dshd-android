import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.ktlint)
  alias(libs.plugins.detekt)
}

val embeddedSetting = System.getenv("DSHD_EMBED_ROOTFS")
val defaultRootfs = File(
  rootProject.projectDir,
  "dist/dshd-rootfs-arm64-webview-pnpm-appboot-mobile-fixed22-nativepdf.tar.xz"
)
var embedRootfs = true
var embeddedRootfs = defaultRootfs
if (embeddedSetting != null) {
  if (embeddedSetting in setOf("none", "0", "false")) {
    embedRootfs = false
  } else if (embeddedSetting.trim().isNotEmpty()) {
    embeddedRootfs = File(embeddedSetting)
  }
}

fun sha256(file: File): String {
  val digest = MessageDigest.getInstance("SHA-256")
  file.inputStream().use { input ->
    val buffer = ByteArray(1 shl 16)
    while (true) {
      val read = input.read(buffer)
      if (read <= 0) break
      digest.update(buffer, 0, read)
    }
  }
  return digest.digest().joinToString("") { "%02x".format(it) }
}

if (embedRootfs && !embeddedRootfs.isFile) {
  throw GradleException(
    "embedded rootfs missing: $embeddedRootfs; set DSHD_EMBED_ROOTFS=/path/to/rootfs.tar.xz " +
      "or DSHD_EMBED_ROOTFS=none for a small debug APK"
  )
}
val embeddedVersion = if (embedRootfs) sha256(embeddedRootfs) else ""
val generatedRootfsAssets = layout.buildDirectory.dir("generated/embeddedRootfs")

android {
  namespace = "com.dshd.android"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.dshd.android"
    minSdk = 28
    targetSdk = 28
    versionCode = 6
    versionName = "0.3.3"
    buildConfigField("String", "ROOTFS_URL", "\"${System.getenv("DSHD_ROOTFS_URL") ?: ""}\"")
    buildConfigField("String", "ROOTFS_SHA256", "\"${System.getenv("DSHD_ROOTFS_SHA256") ?: ""}\"")
    buildConfigField("boolean", "ROOTFS_EMBEDDED", embedRootfs.toString())
    buildConfigField("String", "ROOTFS_VERSION", "\"$embeddedVersion\"")
  }

  signingConfigs {
    val keystore = System.getenv("DSHD_KEYSTORE")
    if (keystore != null) {
      create("release") {
        storeFile = file(keystore)
        storePassword = System.getenv("DSHD_KEYSTORE_PASSWORD")
        keyAlias = System.getenv("DSHD_KEY_ALIAS")
        keyPassword = System.getenv("DSHD_KEY_PASSWORD")
        enableV1Signing = false
        enableV2Signing = true
        enableV3Signing = true
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      signingConfig = signingConfigs.findByName("release")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    buildConfig = true
  }

  androidResources {
    noCompress += "xz"
  }

  sourceSets {
    getByName("main") {
      if (embedRootfs) assets.srcDir(generatedRootfsAssets)
    }
  }

  packaging {
    jniLibs {
      useLegacyPackaging = true
    }
  }

  // targetSdk 28 is intentional: Cortex runs the glibc rootfs without Android's
  // newer seccomp/W^X restrictions. Do not fail release builds on Play's SDK warning.
  lint {
    disable += "ExpiredTargetSdkVersion"
  }
}

kotlin {
  jvmToolchain(17)
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
  }
}

ktlint {
  android = true
  ignoreFailures = false
  filter {
    exclude { it.file.path.contains("/build/") }
  }
}

detekt {
  buildUponDefaultConfig = true
  source.setFrom(files("src/main/kotlin", "src/test/kotlin"))
  baseline = file("$rootDir/config/detekt/baseline.xml")
  parallel = true
}

dependencies {
  // Pure-Java XZ decoder for the one-time rootfs import (no NDK dependency).
  implementation(libs.xz)
  testImplementation(libs.junit)
}

if (embedRootfs) {
  tasks.register<Copy>("copyEmbeddedRootfs") {
    from(embeddedRootfs)
    into(generatedRootfsAssets)
    rename { "rootfs.tar.xz" }
  }
  tasks.named("preBuild") {
    dependsOn("copyEmbeddedRootfs")
  }
}
