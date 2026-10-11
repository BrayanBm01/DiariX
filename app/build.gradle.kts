import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// URL base de la API de DiariX.
//
// La app es la MISMA para cualquier red: no conoce Wi-Fi concreta ni
// direcciones de la LAN. Lo único que cambia es la URL del backend, que se
// inyecta en tiempo de compilación y queda dentro de `BuildConfig`:
//
//  - DEBUG  → servidor de desarrollo. Por defecto el host visto desde el
//             emulador (10.0.2.2 = la máquina que corre Android Studio). Se
//             puede apuntar a la LAN de desarrollo con
//             `-PdiarixDebugBaseUrl=http://192.168.x.x:3000/api/v1`, pero
//             SOLO en debug.
//  - RELEASE → backend PÚBLICO por HTTPS. NO hay valor por defecto: si no se
//             define `diarixReleaseBaseUrl`, el build de release FALLA con un
//             mensaje claro en vez de publicar un APK que solo funcione en la
//             red del desarrollador. Tampoco se admite una IP privada.
//
//   diarixDebugBaseUrl=http://192.168.0.111:3000/api/v1
//   diarixReleaseBaseUrl=https://api.tu-dominio.com/api/v1
val diarixDebugBaseUrl =
    providers.gradleProperty("diarixDebugBaseUrl").getOrElse("http://10.0.2.2:3000/api/v1")

val diarixReleaseBaseUrl =
    providers.gradleProperty("diarixReleaseBaseUrl").getOrElse("")

/** IP privada, loopback o el host del emulador: nunca en release. */
val direccionSoloDesarrollo = Regex(
    """^https?://(localhost|127\.|0\.0\.0\.0|10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.)""",
)

// Web OAuth Client ID de Google (Fase 4): es el MISMO valor que el backend
// acepta en `GOOGLE_CLIENT_ID`. Con este id la app pide un idToken cuyo
// `aud` coincide con lo que verifica el servidor.
// Dejar vacío solo deshabilita el botón "Continuar con Google" en esta
// versión de la app; el acceso empresarial sigue funcionando.
//   diarixGoogleWebClientId=xxxxx.apps.googleusercontent.com
val diarixGoogleWebClientId =
    providers.gradleProperty("diarixGoogleWebClientId").getOrElse("")

android {
    namespace = "com.example.totaldiaria"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.totaldiaria.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "DIARIX_GOOGLE_WEB_CLIENT_ID", "\"$diarixGoogleWebClientId\"")
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("totaldiaria-release.jks")
            storePassword = providers.gradleProperty("TOTALDIARIA_STORE_PASSWORD").get()
            keyAlias = providers.gradleProperty("TOTALDIARIA_KEY_ALIAS").get()
            keyPassword = providers.gradleProperty("TOTALDIARIA_KEY_PASSWORD").get()
        }
    }

    buildTypes {
        debug {
            // Desarrollo: puede ser HTTP (emulador o LAN de desarrollo).
            buildConfigField("String", "DIARIX_BASE_URL", "\"$diarixDebugBaseUrl\"")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Producción: SOLO un backend público por HTTPS. Si la propiedad
            // no está definida, la tarea de release falla antes de compilar
            // (validación de abajo); nunca se publica una URL de desarrollo.
            buildConfigField("String", "DIARIX_BASE_URL", "\"$diarixReleaseBaseUrl\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// El release SOLO puede apuntar a un backend público por HTTPS. Se valida al
// ejecutar tareas de release (nunca al compilar debug): así se falla pronto y
// con un mensaje accionable en vez de empaquetar una URL que solo funciona en
// la red del desarrollador.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        val url = diarixReleaseBaseUrl
        check(url.isNotBlank()) {
            "Falta diarixReleaseBaseUrl. Define el backend PÚBLICO por HTTPS, por ejemplo: " +
                "-PdiarixReleaseBaseUrl=https://api.tu-dominio.com/api/v1"
        }
        check(url.startsWith("https://")) {
            "diarixReleaseBaseUrl debe usar HTTPS (recibido: $url)."
        }
        check(!direccionSoloDesarrollo.containsMatchIn(url)) {
            "diarixReleaseBaseUrl no puede ser localhost ni una IP privada: $url. " +
                "El APK debe funcionar desde cualquier Wi-Fi y con datos móviles."
        }
    }
}
dependencies {
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    implementation("androidx.cardview:cardview:1.0.0")

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    // Fase 4 · Conectividad con DiariX
    implementation(libs.okhttp)
    // Fase 4 · Google Sign-In (usuario individual)
    implementation(libs.play.services.auth)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}