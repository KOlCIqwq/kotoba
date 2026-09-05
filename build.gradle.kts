plugins {
    alias(mihonx.plugins.android.library)
}

val mangaNdkVersion = "29.0.13599879"
val nativeBuildDir = layout.buildDirectory.dir("generated/manga-native")
val nativeJniDir = nativeBuildDir.map { it.dir("jniLibs") }
val nativeObject = nativeBuildDir.map { it.file("ncnn_jni.o") }
val nativeLibrary = nativeJniDir.map { it.file("arm64-v8a/libmanga_ncnn.so") }

android {
    namespace = "mihon.feature.translation.engine"
    // Use the installed NDK. The upstream engine is pinned to 28.x, which
    // is not present in this development environment.
    ndkVersion = mangaNdkVersion

    defaultConfig {
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    androidResources {
        noCompress += "onnx"
    }

    sourceSets {
        getByName("main").jniLibs.srcDir(nativeJniDir.get().asFile)
    }
}

val androidSdkDir = file(System.getenv("ANDROID_HOME") ?: "${System.getenv("LOCALAPPDATA")}/Android/Sdk")
val nativeToolchainBin = androidSdkDir.resolve(
    "ndk/$mangaNdkVersion/toolchains/llvm/prebuilt/windows-x86_64/bin",
)
val nativeSysroot = androidSdkDir.resolve(
    "ndk/$mangaNdkVersion/toolchains/llvm/prebuilt/windows-x86_64/sysroot",
)
val nativeInclude = layout.projectDirectory.dir("src/main/cpp/ncnn/include/ncnn")
val nativeLibDir = layout.projectDirectory.dir("src/main/cpp/ncnn/lib")

val prepareMangaNative by tasks.registering {
    outputs.dir(nativeBuildDir)
    doLast {
        nativeObject.get().asFile.parentFile.mkdirs()
        nativeLibrary.get().asFile.parentFile.mkdirs()
    }
}

val compileMangaNcnn by tasks.registering(Exec::class) {
    dependsOn(prepareMangaNative)
    inputs.file("src/main/cpp/ncnn_jni.cpp")
    inputs.dir(nativeInclude)
    outputs.file(nativeObject)
    executable = nativeToolchainBin.resolve("clang++.exe").absolutePath
    args(
        "--target=aarch64-none-linux-android26",
        "--sysroot=${nativeSysroot.invariantSeparatorsPath}",
        "-fPIC",
        "-std=c++17",
        "-O3",
        "-ffast-math",
        "-fopenmp",
        "-fno-rtti",
        "-fno-exceptions",
        "-I${nativeInclude.asFile.invariantSeparatorsPath}",
        "-c",
        layout.projectDirectory.file("src/main/cpp/ncnn_jni.cpp").asFile.invariantSeparatorsPath,
        "-o",
        nativeObject.get().asFile.invariantSeparatorsPath,
    )
}

val linkMangaNcnn by tasks.registering(Exec::class) {
    dependsOn(compileMangaNcnn)
    inputs.file(nativeObject)
    inputs.dir(nativeLibDir)
    outputs.file(nativeLibrary)
    executable = nativeToolchainBin.resolve("clang++.exe").absolutePath
    args(
        "--target=aarch64-none-linux-android26",
        "--sysroot=${nativeSysroot.invariantSeparatorsPath}",
        "-shared",
        "-static-libstdc++",
        "-o",
        nativeLibrary.get().asFile.invariantSeparatorsPath,
        nativeObject.get().asFile.invariantSeparatorsPath,
        nativeLibDir.file("libncnn.a").asFile.invariantSeparatorsPath,
        nativeLibDir.file("libglslang.a").asFile.invariantSeparatorsPath,
        nativeLibDir.file("libSPIRV.a").asFile.invariantSeparatorsPath,
        nativeLibDir.file("libOSDependent.a").asFile.invariantSeparatorsPath,
        nativeLibDir.file("libMachineIndependent.a").asFile.invariantSeparatorsPath,
        nativeLibDir.file("libGenericCodeGen.a").asFile.invariantSeparatorsPath,
        nativeLibDir.file("libglslang-default-resource-limits.a").asFile.invariantSeparatorsPath,
        "-llog",
        "-landroid",
        "-ljnigraphics",
        "-ldl",
        "-lz",
        "-latomic",
        "-lm",
    )
}

tasks.matching {
    it.name.startsWith("merge") && (it.name.endsWith("NativeLibs") || it.name.endsWith("JniLibFolders"))
}.configureEach {
    dependsOn(linkMangaNcnn)
}

dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
    implementation(libs.bundles.kotlinx.coroutines)
    implementation(libs.bundles.okhttp)
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

