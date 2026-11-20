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
//  - DEBUG  → el MISMO backend de producción (Render), para que un APK de
//             debug funcione en cualquier teléfono sin servidor local. Se
//             puede apuntar a un backend local con
//             `-PdiarixDebugBaseUrl=http://192.168.x.x:3000/api/v1`, pero
//             SOLO en debug.
//  - RELEASE → backend PÚBLICO por HTTPS, con valor por defecto (ver abajo):
//             `./gradlew :app:bundleRelease` funciona sin recordar ninguna
//             bandera y SIEMPRE apunta al MISMO backend que usa la web.
//             Se puede sobreescribir con `-PdiarixReleaseBaseUrl=...` y las
//             validaciones de abajo siguen aplicando (HTTPS, sin IP privada).
//
//   diarixDebugBaseUrl=http://192.168.0.111:3000/api/v1
//   diarixReleaseBaseUrl=https://api.tu-dominio.com/api/v1

/**
 * Backend de PRODUCCIÓN de DiariX.
 *
 * Es la MISMA URL que usa la web en producción: `VITE_API_URL` de
 * `Diarix/frontend/.env.production` (`https://diarix-backend.onrender.com/api/v1`).
 * Que las dos apunten al mismo sitio es la garantía de que una cuenta creada
 * en la web existe para la app, y viceversa.
 *
 * El backend migró de hosting: el dominio antiguo ya no sirve la API
 * (responde 404 «Application not found» a todo) y una app compilada contra él
 * fallaba al registrarse e iniciar sesión con
 * «El servidor no respondió como la API de DiariX (HTTP_404)». El valor viejo
 * quedó solo en el historial de git.
 *
 * Si algún día el backend cambia de dominio, hay que cambiarlo AQUÍ y en
 * `VITE_API_URL`, y publicar de nuevo la web y la app.
 */
val URL_BACKEND_PRODUCCION =
    "https://diarix-backend.onrender.com/api/v1"

/**
 * Backend por defecto para DEBUG.
 *
 * Es el MISMO backend de producción (Render), no un servidor local: la app de
 * debug debe poder usarse en un teléfono real sin que el desarrollo esté
 * encendido. Render arranca en frío, así que los timeouts de red ya están
 * dimensionados para eso (ver `ApiCliente`).
 *
 * Una propiedad `diarixDebugBaseUrl` en `gradle.properties` (del proyecto o del
 * usuario) puede apuntar a un backend local para pruebas; si queda vieja, es la
 * causa clásica de «El servidor de DiariX tardó demasiado»: la app intenta
 * alcanzar una IP que ya no existe.
 */
val diarixDebugBaseUrl =
    providers.gradleProperty("diarixDebugBaseUrl").getOrElse(URL_BACKEND_PRODUCCION)

val diarixReleaseBaseUrl =
    providers.gradleProperty("diarixReleaseBaseUrl").getOrElse(URL_BACKEND_PRODUCCION)

/** IP privada, loopback o el host del emulador: nunca en release. */
val direccionSoloDesarrollo = Regex(
    """^https?://(localhost|127\.|0\.0\.0\.0|10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.)""",
)

// Web OAuth Client ID de Google: es el MISMO valor que el backend acepta en
// `GOOGLE_CLIENT_ID` y el que usa la web en `VITE_GOOGLE_CLIENT_ID`.
// Con Credential Manager, `GetGoogleIdOption.setServerClientId(...)` hace que
// el `idToken` devuelto tenga este valor como `aud`, que es lo que valida el
// servidor. Está en UN solo sitio: aquí (BuildConfig). Los Client IDs de tipo
// Android solo se registran en Google Cloud; NO se escriben en el proyecto.
//
// Se puede sobreescribir sin editar el archivo:
//   -PdiarixGoogleWebClientId=...  o  DIARIX_GOOGLE_WEB_CLIENT_ID=...
val diarixGoogleWebClientId =
    providers.gradleProperty("diarixGoogleWebClientId")
        .orElse(providers.environmentVariable("DIARIX_GOOGLE_WEB_CLIENT_ID"))
        .getOrElse("817402741819-66kejag11k8aq1078an532eb7ld87vjg.apps.googleusercontent.com")

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
            // El sufijo hace visible en Ajustes/`dumpsys` que se instaló un
            // build de desarrollo. Fue justo lo que faltó para detectar que el
            // APK del teléfono no era el release que se creía.
            versionNameSuffix = "-debug"
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

        // Un release sin este valor entra en la rama local que deshabilita el
        // botón de Google: el usuario ve un error y NINGUNA petición llega al
        // backend. Antes era solo un aviso; fallar aquí evita publicar otro APK
        // con el botón mudo. El valor por defecto ya vive en este archivo.
        check(diarixGoogleWebClientId.isNotBlank()) {
            "Falta el Web Client ID de Google. Pásalo con " +
                "-PdiarixGoogleWebClientId=... o con la variable " +
                "DIARIX_GOOGLE_WEB_CLIENT_ID, y configura el MISMO valor en " +
                "GOOGLE_CLIENT_ID del backend."
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
    // Google Sign-In con Credential Manager (usuario individual).
    // `credentials-play-services-auth` aporta el proveedor de Google Play Services.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}