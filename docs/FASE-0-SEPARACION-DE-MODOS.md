# DIARIX — FASE 0: separación de FACTURACIÓN y PRÉSTAMOS

**Qué hace esta fase:** separa DiariX en dos áreas que nunca se mezclan y deja
la estructura preparada para que el sistema de préstamos crezca dentro.

**Qué NO hace:** no implementa nada de préstamos. Ni clientes, ni préstamos,
ni cuotas, ni cobros, ni pagos, ni Mercado Pago, ni portal, ni dashboard
financiero. Eso son las fases siguientes.

---

> ## ⚠️ Actualizado por la FASE 0.1
>
> Este documento nació como la memoria de la Fase 0 y **se conserva entero**,
> porque explica el porqué de decisiones que la Fase 0.1 no toca. Pero hay
> partes suyas que la Fase 0.1 cambió, y están marcadas en el sitio con
> **[Fase 0.1]**:
>
> | De la Fase 0 | Ahora |
> |---|---|
> | Una **tira de área permanente** en las seis pantallas, con "Cambiar" | **Retirada.** El cambio de área vive solo en Configuración → Modo de DiariX |
> | Se entra siempre en el área inicial sin preguntar nada | **Se pregunta una vez** al entrar por primera vez, y nunca más |
> | `SelectorDeModo.enlazar` pinta la tira | **Eliminado.** El diálogo se llama `mostrarComoDialogo` |
> | Botón "Ir a Facturación" dentro de Préstamos | **Retirado.** Préstamos abre Configuración con el engranaje de su encabezado |
> | Configuración: cuatro botones del mismo tamaño | **Configuración por secciones**, con perfil, cuenta, comprobantes, datos e información |
> | Sin pantalla de perfil | **Perfil real** con lo único que el backend deja cambiar |
> | 77 tests antes de la Fase 0 → 90 después | **148** tras la Fase 0.1 (77 → 90 → 93 → 148) |
>
> Todo lo demás —la separación por paquetes, el menú por área, el mapa de
> destinos, `CLEAR_TASK`, los colores, la base de datos intacta— sigue igual.
>
> **La Fase 0.1 entera está en la sección 13.**

---

## 1. Lo que se encontró en el proyecto

### 1.1 Son dos proyectos, no uno

| Ruta | Qué es | Estado |
|---|---|---|
| `~/Documentos/apps/TotalDiaria` | App Android (**este repositorio**) | Git, 44 archivos Kotlin, +2 sin versionar |
| `~/Documentos/Diarix` | Backend NestJS + Prisma, front React, docs | **Sin Git** (no es repositorio) |

El trabajo de esta fase es en el proyecto Android. El backend se auditó y se
reporta en la sección 6, pero **no se tocó**.

### 1.2 Cómo estaba la app Android

Cosas verificadas, no asumidas:

- **Un solo módulo de Gradle** (`:app`), AGP 9.2.1, `minSdk 26`, `targetSdk 36`.
- **Vistas XML, no Compose.** Cero archivos de Compose en el proyecto.
- **Activities, no Fragments.** Cero Fragments. Una Activity por pantalla.
- **`findViewById`, no ViewBinding.** Ni DataBinding, ni ViewModels, ni LiveData.
- **Sin inyección de dependencias.** El objeto `Diarix` arma el grafo a mano.
- **Sin Navigation Component.** La navegación es `Intent` + flags.
- **Sin coroutines.** `ExecutorService` en `Diarix` y `Thread` + `runOnUiThread`.
- **Room: no.** SQLite crudo con `SQLiteOpenHelper` y repositorios a mano.
- **Retrofit: no.** OkHttp con un cliente HTTP propio (`network/ApiCliente`).

Estructura de paquetes antes de esta fase: 7 Activities en la raíz del paquete y
7 carpetas planas al lado (`models`, `database`, `service`, `sync`, `adapter`,
`ui`, `navigation`, `preferences`). **No existía ningún concepto de área,
módulo ni modo.** El menú inferior (`navigation/BottomNavigator.kt` + `res/menu/menu_bottom.xml`)
era uno solo, compartido por todas las pantallas.

### 1.3 La base de datos local

`facturas.db`, versión **8**, con tres tablas: `facturas`, `registros`,
`papelera`. Las migraciones son incrementales por versión
(`if (oldVersion < N)`) con `try/catch` por `ALTER TABLE`.

**En esta fase no se tocó el esquema.** No se subió de versión, no se añadió
ninguna tabla, no se cambió ningún DDL. Las facturas existentes siguen donde
están y se leen igual. Ver sección 5.

### 1.4 Qué está funcionando hoy

- Registro de facturas (efectivo / transferencia / ambos) con comprobante por foto o galería.
- Total y cantidad del día en el inicio.
- Historial de facturas activas, editar y eliminar.
- Registros diarios + papelera, con filtro por fecha.
- Estadísticas con cuatro gráficas y cuatro periodos.
- Código QR del negocio.
- Respaldo: exportar e importar un ZIP.
- Sesión (Google + empresa), tokens cifrados en Keystore, perfil y plan.
- Sincronización de facturas con el backend, con cola offline e idempotencia.
- Cierre diario idempotente y Estadísticas del periodo.

### 1.5 Estado previo (línea base, antes de tocar nada)

```
./gradlew testDebugUnitTest   →  BUILD SUCCESSFUL
                                 77 tests, 0 failures, 0 errors, 1 skipped
./gradlew :app:assembleDebug   →  BUILD SUCCESSFUL
```

El test omitido es `AlmacenSeguroTest`, preexistente: depende del Android
Keystore y está marcado como `skip`. No se tocó.

---

## 2. Lo que se cambió

### 2.1 La idea: dos áreas, un solo producto

```
                    DIARIX
                       │
        ┌──────────────┴──────────────┐
        │                             │
   FACTURACIÓN                    PRÉSTAMOS
     (verde #0AA87A)               (azul #2B6ED4)
        │                             │
   Inicio · Registros             Clientes · Préstamos
   Historial · Estadísticas       Cuotas · Cobros
   Configuración                  Pagos · Dashboard
```

Sigue siendo **una sola app**: mismo ícono, mismo nombre, mismo login, mismo
logo. Cambia en qué mitad se está y qué aparece en pantalla.

### 2.2 El concepto `ModoDiarix`

`core/modo/ModoDiarix.kt` es la **única** fuente de verdad:

```kotlin
enum class ModoDiarix(
    val clave: String,
    @param:StringRes val titulo: Int,
    @param:StringRes val resumen: Int,
    val pantallaDeInicio: Class<out AppCompatActivity>,
) {
    FACTURACION(..., pantallaDeInicio = MainActivity::class.java),
    PRESTAMOS (..., pantallaDeInicio = PrestamosInicioActivity::class.java),
}
```

De ahí salen las tres cosas que antes no existían:

1. **Qué menú se infla** → `core/modo/MenuDeModo.kt`.
2. **Qué pone la tira de área** → `SelectorDeModo.enlazar`.
3. **A qué pantalla se entra al abrir la app** → `SelectorDeModo.abrirInicio`.

Si mañana aparece un tercer área, se agrega **una fila a este enum** y nada
más. No hay un segundo lugar donde se decida el modo, porque dos lugares se
desincronizan.

### 2.3 Cómo se cambia de área

> **[Fase 0.1] Esta sección describe la tira, que ya no existe.** El cambio de
> área se hace ahora **solo** desde Configuración → "Modo de DiariX" → Cambiar.
> `layout/view_strip_modo.xml` se borró, los seis `<include>` se quitaron y
> `SelectorDeModo.enlazar` se eliminó. Se deja aquí el diseño original porque
> explica por qué el `CLEAR_TASK` de abajo es lo que hace que el "atrás" no
> devuelva al área abandonada: esa parte **sigue siendo cierta** y se sigue
> usando desde el nuevo sitio.

**La tira de área** (`layout/view_strip_modo.xml`): una banda blanca del color
del área, con el nombre y la palabra "Cambiar", debajo del encabezado. Estaba en
**las seis pantallas** de las dos áreas, con el mismo `<include>`:

```
┌─────────────────────────────────────┐
│  ▓  FACTURACIÓN        Cambiar  ▾   │  ← la tira
├─────────────────────────────────────┤
│  ┌───────────────────────────────┐  │
│  │  TOTAL DEL DÍA                │  │
│  │  $ 850.000                    │  │
│  └───────────────────────────────┘  │
```

Tocarla abre el selector: **"¿Qué quieres gestionar?"**, con las dos áreas
lado a lado y la vigente marcada con un borde y la palabra "AQUÍ".

Elegir la otra:

1. Guarda el modo nuevo.
2. Abre la pantalla de inicio de esa área con
   `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`.

El `CLEAR_TASK` es la parte importante: **se borra la pila**. Si no se hiciera,
el botón "atrás" devolvería al usuario a una pantalla del área que acaba de
abandonar, con el menú de la nueva. Es la razón de que cambiar de área no se
sienta como estar en dos aplicaciones.

El mismo `SelectorDeModo` se usa desde las dos mitades, y el mismo diálogo.
No hay dos selectores.

### 2.4 El menú inferior ahora pertenece a un área

Este es el mecanismo que impide que las interfaces se mezclen.

**Antes:** `res/layout/activity_main.xml` (y los otros cuatro) declaraban
`app:menu="@menu/menu_bottom"`. Un solo menú para toda la app, declarado en el
XML, estático.

**Ahora:** ningún layout declara `app:menu`. `BottomNavigator.configurar()`
infla el menú del área vigente:

```kotlin
navView.menu.clear()
navView.inflateMenu(MenuDeModo.de(modoVigente(activity)))
```

| Área | Menú | Pestañas |
|---|---|---|
| FACTURACIÓN | `menu/menu_facturacion.xml` | Historial · Registros · **Inicio** · Estadíst. · Config. |
| PRÉSTAMOS | `menu/menu_prestamos.xml` | Préstamos |

`menu_facturacion.xml` es el `menu_bottom.xml` de antes, con el mismo orden y
las mismas cinco pestañas. Cero cambios en la navegación de facturación.

La segunda barrera está en la resolución de destinos. Antes había un `when`
único con los cinco ids; ahora hay **un mapa por área**:

```kotlin
private val destinosDeFacturacion = mapOf(nav_inicio to MainActivity::class, ...)
private val destinosDePrestamos   = mapOf(nav_prestamos_inicio to PrestamosInicioActivity::class)

private fun destinoPara(activity, itemId) = when (modoVigente(activity)) {
    ModoDiarix.FACTURACION -> destinosDeFacturacion[itemId]
    ModoDiarix.PRESTAMOS   -> destinosDePrestamos[itemId]
}
```

Un id que no exista en el mapa de su área devuelve `null` y la pestaña se
desmarca. Aunque un id se colara en el menú equivocado, **no se abriría una
pantalla del otro área**.

### 2.5 El arranque respeta el área

`MainActivity` sigue siendo el `LAUNCHER`. No se cambió el punto de entrada de
la app: es lo que garantiza que DiariX siga abriéndose como siempre.

Lo que hace ahora, al arrancar:

```
sin sesión  → LoginActivity
con sesión:
   área = FACTURACIÓN → la pantalla de invoices, como siempre
   área = PRÉSTAMOS   → SelectorDeModo.abrirInicio() → PréstamosInicioActivity
```

`LoginActivity.abrirPrincipal()` también entra por `SelectorDeModo.abrirInicio`,
así que la primera pantalla de la sesión es la del área en la que se está,
venga por el camino que venga (primer arranque, sesión caducada, cierre de
sesión).

### 2.6 La estructura de módulos

El árbol de paquetes, después:

```
com/example/totaldiaria/
├── Diarix.kt                  grafo de objetos (sin cambios de lógica)
├── LoginActivity.kt           acceso (sin cambios de lógica)
│
├── core/                      TRANSVERSAL DE LA APP
│   ├── modo/
│   │   ├── ModoDiarix.kt          las dos áreas  ← NUEVO
│   │   ├── AlmacenDeModo.kt       dónde se recuerda  ← NUEVO
│   │   ├── MenuDeModo.kt          área → menú       ← NUEVO
│   │   └── SelectorDeModo.kt      el interruptor     ← NUEVO
│   └── navegacion/
│       └── BottomNavigator.kt     menú por área      ← movido + modificado
│
├── modulos/                   LOS MÓDULOS DE NEGOCIO
│   ├── README.md               la guía de esta carpeta  ← NUEVO
│   ├── facturacion/            MODO 1  (todo lo de antes)
│   │   ├── modelo/                 Factura, PapeleraItem
│   │   ├── repositorio/            DatabaseHelper, FacturaRepository,
│   │   │                           RegistroRepository, PapeleraRepository
│   │   ├── servicio/               DiaService, CierreDiarioService,
│   │   │                           EstadisticasService, RespaldoService,
│   │   │                           ComprobanteStore
│   │   ├── sincronizacion/         SincronizadorFacturas, Conectividad
│   │   ├── adaptador/              FacturaAdapter, RegistroAdapter,
│   │   │                           PapeleraAdapter
│   │   ├── preferencias/           QrManager
│   │   └── ui/                     MainActivity, HistorialActivity,
│   │                               RegistrosActivity, EstadisticasActivity,
│   │                               ConfiguracionActivity,
│   │                               FacturasDelDiaActivity, diálogos,
│   │                               formatos, gráficas
│   └── prestamos/             MODO 2  (andamiaje vacío)
│       └── ui/
│           └── PrestamosInicioActivity.kt   ← NUEVO
│
├── network/                   HTTP: ApiCliente, AuthApi, ErrorApi, FacturasApi
└── sesion/                    AlmacenSeguro, SessionManager, JwtUtil,
                                ModeloSesion, TokenStore
```

Lo que **cambió de sitio** es solo el nombre del paquete. **No cambió ni una
línea de lógica de negocio.** Los archivos se movieron con `git mv` (Git los
registra como renombrados, no como borrados y recreados) y lo único que se
tocó dentro fue la línea `package` y los `import`.

El documento de requisitos pide una carpeta por módulo con modelo, DTO,
servicio, repositorio y UI. Eso se resolvió **adaptado al proyecto real**: esta
app no tiene DTOs (el `dto/` no se crea porque no hay nada que poner), y su
"servicio" y "servicio implementado" son la misma clase porque el código no usa
puertos y adaptadores. Las razones están en `modulos/README.md`.

### 2.7 La pantalla de inicio de préstamos

`modulos/prestamos/ui/PrestamosInicioActivity.kt` + `layout/activity_prestamos_inicio.xml`.

Es un **andamiaje deliberadamente vacío**, y es importante que lo sea:

- Mismo encabezado que facturación (logo, blanco, elevación 4dp): la persona
  tiene que reconocer que sigue dentro de DiariX.
- La misma tira de área, que dice "PRÉSTAMOS".
- Una tarjeta que explica qué es esta área con palabras llanas.
- Una lista **informativa** de lo que va a aparecer: Clientes, Préstamos,
  Cuotas, Cobros, Pagos, Dashboard. **Sin `clickable` a propósito**: un botón
  que no lleva a ninguna parte es peor que no tenerlo.
- Su propio menú inferior, con una sola pestaña.

> **[Fase 0.1] El botón "Ir a Facturación" se retiró.** Dejarlo y añadir además
> la entrada a Configuración habría dejado **dos** sitios para cambiar de área,
> que es justo lo que la Fase 0.1 viene a arreglar. Ahora Préstamos abre
> Configuración con un engranaje en el encabezado (44dp, `#5A6182`), y desde
> ahí se cambia de área. Estar en Préstamos sigue sin ser una puerta sin salida.

### 2.8 Los colores

| | Color | De dónde sale |
|---|---|---|
| FACTURACIÓN | verde `#0AA87A` | El de siempre de DiariX |
| PRÉSTAMOS | azul `#2B6ED4` | El que la app ya usaba para transferencia |

**No se inventó ninguna paleta.** Los dos colores ya estaban en la app. El
indicador de la tira es verde o azul según el área, así que se distingue de un
vistazo sin tener que leer la etiqueta.

---

## 3. Qué pertenece a cada área

### FACTURACIÓN (`modulos/facturacion/`)

Todo lo que hay hoy. Sin excepciones:

- Registro de facturas con comprobante, total del día y límite del plan.
- Historial de facturas activas, edición y eliminación.
- Registros diarios, papelera y filtro por fecha.
- Estadísticas: cuatro periodos, cuatro gráficas, resumen.
- Código QR del negocio.
- Respaldo: exportar e importar ZIP de la base de datos.
- Cierre diario y consolidación por día.
- Sincronización de facturas con el backend, con cola offline e idempotencia.
- Sus cinco pestañas: Inicio, Registros, Historial, Estadísticas, Configuración.

**Su dashboard sigue siendo su dashboard.** No tiene un solo indicador de
préstamos, y no se le agregó ninguno.

### PRÉSTAMOS (`modulos/prestamos/`)

Solo existe la pantalla de inicio y el menú de una pestaña. Lo demás está
**nombrado y preparado**, no construido: Clientes, Préstamos, Cuotas, Cobros,
Pagos, Dashboard de préstamos. Más adelante, en este mismo orden según el
documento de requisitos: Portal del cliente, Suscripción.

### Transversal (`core/`, `network/`, `sesion/`)

El área en la que se está, la navegación, el cliente HTTP y la sesión. No
pertenecen a ninguna mitad y no saben de ninguna: no importa si el área es
facturación o préstamos para que funcionen.

---

## 4. Archivos

### 4.1 Nuevos (13)

| Archivo | Para qué |
|---|---|
| `core/modo/ModoDiarix.kt` | Las dos áreas. Fuente única de verdad. |
| `core/modo/AlmacenDeModo.kt` | Persistencia del área elegida |
| `core/modo/MenuDeModo.kt` | Área → menú inferior |
| `core/modo/SelectorDeModo.kt` | La tira, el diálogo y el cambio de área |
| `modulos/prestamos/ui/PrestamosInicioActivity.kt` | Inicio del área de préstamos |
| `modulos/README.md` | La guía de la estructura de módulos |
| `res/layout/view_strip_modo.xml` | La tira de área (componente compartido) |
| `res/layout/dialog_selector_modo.xml` | "¿Qué quieres gestionar?" |
| `res/layout/activity_prestamos_inicio.xml` | Pantalla de inicio de préstamos |
| `res/layout/item_modulo_previsto.xml` | Fila informativa de un módulo futuro |
| `res/menu/menu_prestamos.xml` | Menú del área de préstamos |
| `res/drawable/ic_prestamos.xml` | Ícono de la pestaña |
| `res/drawable/bg_modo_facturacion.xml`<br>`res/drawable/bg_modo_prestamos.xml`<br>`res/drawable/bg_opcion_modo.xml`<br>`res/drawable/bg_opcion_modo_activa.xml` | Colores y fondos de los componentes de área |
| `test/.../core/modo/ModoDiarixTest.kt` | 13 pruebas de las garantías del área |

### 4.2 Modificados (11)

| Archivo | Cambio |
|---|---|
| `core/navegacion/BottomNavigator.kt` | Movido + infla el menú del área + un mapa de destinos por área |
| `AndroidManifest.xml` | Rutas de las Activities movidas + `PrestamosInicioActivity` |
| `modulos/facturacion/ui/MainActivity.kt` | Enlaza la tira + arranca en el área vigente |
| `LoginActivity.kt` | `abrirPrincipal()` entra por área |
| `Diarix.kt` | Solo la documentación: aclara qué cubre el grafo |
| `res/menu/menu_facturacion.xml` | Renombrado desde `menu_bottom.xml`. **Contenido idéntico** |
| `res/layout/activity_main.xml` | + tira de área − `app:menu` |
| `res/layout/activity_historial.xml` | + tira de área − `app:menu` |
| `res/layout/activity_registros.xml` | + tira de área − `app:menu` |
| `res/layout/activity_estadisticas.xml` | + tira de área − `app:menu` |
| `res/layout/activity_configuracion.xml` | + tira de área − `app:menu` |
| `res/values/strings.xml` | Los textos de las áreas y de los módulos futuros |

Los cinco layouts de facturación recibieron **dos cambios mecánicos**: una
línea con el `<include>` de la tira, y quitar `app:menu`. Nada más.

### 4.3 Movidos de paquete (36 archivos Kotlin + 3 de prueba)

Cambió la línea `package` y los `import`. **Ni una línea de lógica.**

| De | A |
|---|---|
| `MainActivity.kt` y las otras 5 Activities | `modulos/facturacion/ui/` |
| `models/` | `modulos/facturacion/modelo/` |
| `database/` | `modulos/facturacion/repositorio/` |
| `service/` | `modulos/facturacion/servicio/` |
| `sync/` | `modulos/facturacion/sincronizacion/` |
| `adapter/` | `modulos/facturacion/adaptador/` |
| `preferences/` | `modulos/facturacion/preferencias/` |
| `ui/` y `ui/graficas/` | `modulos/facturacion/ui/` |
| `navigation/BottomNavigator.kt` | `core/navegacion/` |
| `test/database/`, `test/service/`, `test/sync/` | `test/modulos/facturacion/...` |

Sin tocar: `network/` (4), `sesion/` (5), `LoginActivity.kt`, `Diarix.kt`, la
base de datos, los servicios, los repositorios, los adaptadores, las
actividades de facturación, las gráficas.

---

## 5. Datos

**No se tocó la base de datos.**

- `facturas.db` sigue en **versión 8**. Sin migraciones.
- Ni una tabla nueva, ni un campo nuevo, ni un índice nuevo.
- Ni una sentencia `ALTER`, ni una sentencia `DROP`.
- `onCreate` y `onUpgrade` de `DatabaseHelper`: sin cambios.

Las facturas existentes se siguen leyendo con las mismas consultas y se siguen
mostrando en el mismo sitio. Los 11 tests de `FacturaRepositoryTest` y los 8
de `ColaDeSincronizacionTest` cubren exactamente eso —contador del día, cierre
diario, archivo, histórico, cola de sincronización— y siguen en verde.

**No se implementaron tablas de préstamos.** No hay clientes, ni préstamos, ni
cuotas, ni pagos en la base local, ni en el servidor, ni en el esquema Prisma.

---

## 6. Backend

`~/Documentos/Diarix/backend` es **NestJS 11 + Prisma 6 + PostgreSQL**, y ya
está modularizado. **No se le tocó nada.**

Lo que ya cumple del documento de requisitos:

- `src/modules/` con 14 módulos: `auth`, `facturas`, `pagos`, `suscripciones`,
  `planes`, `usuarios`, `empresas`, `conductores`, `vehiculos`, `estadisticas`,
  `auditoria`, `admin`, `eventos`, `salud`.
- Un módulo usa el **servicio** de otro, nunca su repositorio. Por ejemplo
  `FacturasModule` importa `SuscripcionesModule` y consume su servicio.
- Multi-inquilino: `businessId` como columna de inquilino, y sale del token.
- Migraciones versionadas de Prisma (5 hasta hoy).

Lo que falta y **no** se hizo aquí porque es trabajo de fases posteriores:

| Falta | Dónde |
|---|---|
| Módulos `clientes`, `prestamos`, `cuotas`, `cobros`, `dashboard`, `portal` | `src/modules/` |
| Tablas de préstamos en Prisma + migración | `prisma/schema.prisma` |
| Flyway (el documento lo pide; hoy el proyecto usa **Prisma Migrate**) | Migración de tooling, no de esta fase |
| Capa de repositorio (hoy los servicios usan Prisma directo) | `src/modules/*/` |

---

## 7. Pruebas

### 7.1 Antes y después

```
ANTES   ./gradlew testDebugUnitTest   →  77 tests, 0 failures, 0 errors, 1 skipped
ANTES   ./gradlew :app:assembleDebug   →  BUILD SUCCESSFUL

DESPUÉS ./gradlew testDebugUnitTest   →  90 tests, 0 failures, 0 errors, 1 skipped
DESPUÉS ./gradlew :app:assembleDebug   →  BUILD SUCCESSFUL
```

**Los 77 tests de antes siguen en verde**, ahora bajo el paquete nuevo. Ninguno
se reescribió ni se desactivó: solo se les movió la cabecera `package`.

El test omitido es el mismo de antes: `AlmacenSeguroTest`, que depende del
Android Keystore. No se tocó.

### 7.2 Las 13 pruebas nuevas

`test/java/com/example/totaldiaria/core/modo/ModoDiarixTest.kt`

| Prueba | Qué garantiza |
|---|---|
| `sin elegir nada se entra en facturacion` | **Quien actualiza la app ve exactamente lo que veía** |
| `el area inicial abre la pantalla de inicio de facturacion` | El arranque por defecto es la pantalla de siempre |
| `el area elegida se conserva al releerla` | Sobrevive al cierre de la app (se relee de disco, no de memoria) |
| `cada modo tiene su propia pantalla de inicio` | Las dos áreas no comparten pantalla |
| `las claves de los modos no se repiten` | Una clave por modo, para que no se pisen al persistir |
| `una clave desconocida no corresponde a ningun modo` | Clave vieja o corrupta: se detecta |
| `una clave desconocida cae en facturacion en vez de fallar` | Una clave ilegible **no deja la app sin pantalla de inicio** |
| `una clave guardada a mano e ilegible se lee como facturacion` | Idéntico, pero passando por el almacenamiento de verdad |
| `el menu de facturacion conserva sus cinco pestañas` | Las cinco de antes, en el mismo orden, ni una más ni una menos |
| `el menu de prestamos no tiene ninguna pestaña de facturacion` | **Las interfaces no se mezclan** |
| `el menu de facturacion no tiene ninguna pestaña de prestamos` | Y por el otro lado tampoco |
| `cada modo infla un menu distinto` | No hay un menú compartido |
| `las seis pantallas de las dos areas estan declaradas en el manifiesto` | Ninguna pestaña apunta a una Activity inexistente |

### 7.3 Lo que los tests no cubren

No se puede verificar sin un dispositivo o emulador, y queda pendiente de
revisión manual:

- Que la tira se vea bien en pantalla real en los seis sitios.
- Que el diálogo "¿Qué quieres gestionar?" se lea bien y los taps funcionen.
- Que el `CLEAR_TASK` al cambiar de área se comporte bien con el botón "atrás".
- Que la pantalla de préstamos se vea bien en pantallas pequeñas.

**Recomendación: antes de dar por buena la fase, instalar el APK de debug y
recorrer las seis pantallas de facturación y la de préstamos.** Es la parte
que ninguna prueba automática de esta fase cubre.

### 7.4 Lo que sí cubren los tests existentes, sin tocar

- Registro y consulta de facturas.
- Cierre diario: archivo por día, cambio de mes y de año, idempotencia.
- Conservación del histórico tras el cierre.
- Cola de sincronización: idempotencia, estados, reintentos.
- Sesión: tokens, refresco rotativo, expiración, cierre de sesión.
- Errores de la API.

---

## 8. Decisiones y sus porqués

| Decisión | Por qué |
|---|---|
| Un solo módulo de Gradle, carpetas por módulo | Partir el build en módulos de Gradle daría compilación más rápida, que no es el problema. La separación lógica es la que cuesta caro retrofitar. |
| El menú se infla desde el código, no con `app:menu` | `app:menu` es estático y es el mismo para todas las pantallas. Con él no se puede tener un menú por área sin acordarse de cada layout. |
| Un mapa de destinos por área | Un `when` único con los diez ids no impide que un id mal puesto abra una pantalla del otro área. Con el mapa, salta a `null`. |
| `MAIN | CLEAR_TASK` al cambiar de área | Si la pila no se limpia, "atrás" devuelve al área abandonada con el menú de la nueva. Es la causa clásica de "sentir que son dos apps". |
| FACTURACIÓN como área inicial | DiariX hasta hoy **es** facturación. Quien ya la usaba no debe tener que elegir nada. |
| La lista de módulos futuros no es navegable | Un botón que no lleva a ninguna parte es peor que no tenerlo. |
| Préstamos con una sola pestaña | Una pestaña sin pantalla detrás sería un botón que no hace nada. La segunda entra con su módulo. |
| El área se guarda aparte de `Diarix` | `Diarix` es el grafo de datos. El área es una decisión de navegación. Mezclarlas haría que cambiar de área reconstruyera la base de datos. |
| `SharedPreferences` para el área | No es un dato sensible. Los tokens sí van cifrados (`AlmacenSeguro`); un modo de trabajo no necesita cifrarse. |
| Textos de las áreas en `strings.xml` | El título se pinta en tres sitios. Si estuviera escrito tres veces, dejarían de coincidir. |
| La barra verde/azul | Los dos colores ya estaban en la app. Nada de paleta nueva. |

---

## 9. Riesgos

| Riesgo | Gravedad | Estado |
|---|---|---|
| La tira ocupa ~38dp en las cinco pantallas de facturación | Baja | Es un coste deliberado: es lo que hace visible el área. Si molesta, se hace más fina; no se quita. |
| No se pudo probar en un dispositivo real | **Media** | Pendiente. Ver 7.3. |
| `network/` y `sesion/` quedaron fuera de los módulos | Baja | Son infraestructura transversal y ya funcionaban. Moverlos no añade nada y sí añade riesgo. |
| `core.modo` conoce clases concretas de ambos módulos | Baja | Es el precio de tener el ruteo en un solo lugar. Está aislado en `ModoDiarix`. |
| La base local no tiene tablas de préstamos | — | Correcto: se hacen cuando exista el módulo, no antes. |
| La base de datos de la app no tiene `tenant_id` | Baja en esta fase | La usa el backend, no la app. El requisito es del modelo de datos del servidor. |

---

## 10. Lo que deliberadamente NO se implementó

Todo esto es de fases posteriores:

- Clientes (RF-01 a RF-04): registro, consulta, referencias, PIN.
- Préstamos (RF-05 a RF-09): calendario de cuotas, cálculo, estados.
- Cuotas: generación y saldos.
- Cobros (RF-15 a RF-17): del día, vencidos, atraso, próximos.
- Pagos (RF-10 a RF-14): parciales, soportes, anulaciones, historial.
- Dashboard financiero (RF-18 a RF-20): prestado, recuperado, ganancia.
- Portal del cliente (RF-21 a RF-24).
- Suscripción (RF-28 a RF-30) y Mercado Pago.
- Sincronización offline de préstamos (RF-27).
- Tablas de préstamos en SQLite y en Prisma.
- Módulos nuevos en el backend.
- Flyway.
- Vista web del súper administrador.

---

## 11. Validación final

| # | Comprobación | Resultado |
|---|---|---|
| 1 | La aplicación compila | ✅ `assembleDebug` BUILD SUCCESSFUL |
| 2 | Los tests existentes pasan | ✅ 77 de antes, 0 fallos; 90 en total |
| 3 | Facturación continúa funcionando | ✅ Sin cambios de lógica; sus 5 pantallas intactas |
| 4 | Las facturas existentes siguen disponibles | ✅ Base sin tocar; los tests de SQLite y de cierre en verde |
| 5 | El dashboard actual sigue funcionando | ✅ `MainActivity` sin cambios de cálculo ni de diseño |
| 6 | Se puede entrar al modo FACTURACIÓN | ✅ Es el área inicial; siempre alcanzable |
| 7 | Se puede entrar al modo PRÉSTAMOS | ✅ Desde la tira, en cualquiera de las dos áreas |
| 8 | El modo PRÉSTAMOS está claramente separado | ✅Paquete propio, menú propio, pantalla propia |
| 9 | Facturación no aparece mezclada con préstamos | ✅ Menú por área + mapa de destinos por área + 3 pruebas que lo comprueban |
| 10 | Las opciones futuras están preparadas para crecer | ✅ `MenuDeModo`, mapas de destinos, capas por módulo, guía en `modulos/README.md` |
| 11 | No hay pantallas duplicadas | ✅ Un solo `SelectorDeModo`, una sola tira (un `<include>`), un solo diálogo |
| 12 | No se rompió la navegación existente | ✅ Las 5 pestañas y sus reglas de pila, sin cambios |
| 13 | No se implementó nada de fases posteriores | ✅ Préstamos es un andamiaje vacío con una lista informativa |
| 14 | No hubo cambios destructivos | ✅ Sin cambios en la base de datos, sin `DROP`, sin cambios de esquema |

**Pendiente de revisión humana:** la comprobación en dispositivo real (7.3).

---

## 12. Para la Fase 1

El orden que el proyecto ya tiene decidido, para cuando se retome:

1. Instalar el APK de debug y recorrer las seis pantallas + el selector.
2. Cliente: `modulos/clientes/` con `modelo`, `repositorio`, `servicio`, `ui`.
   Su tabla en SQLite y en Prisma, con `tenant_id` en el servidor.
3. Préstamo: `modulos/prestamos/`, con su calendario de cuotas.

Lo primero que hará un módulo nuevo, siempre: agregar su pestaña al menú de
**su** área y su `Activity` al manifiesto. Si necesita un área nueva, una fila
más en `ModoDiarix`.
---
---

# FASE 0.1 — La primera vez, la ficha y una Configuración de verdad

**Qué hace esta fase:** le pregunta a la persona en qué área de DiariX va a
trabancar (una sola vez), retira la tira de área permanente, convierte
Configuración en una pantalla organizada por secciones y le da un perfil real.

**Qué NO hace:** no implementa nada de préstamos (sigue siendo el mismo
andamiaje vacío), no toca la lógica de facturación, no añade endpoints, no
empieza la Fase 1.

**Qué NO se rehace:** la arquitectura de la Fase 0. `ModoDiarix`,
`AlmacenDeModo`, `MenuDeModo` y `SelectorDeModo` siguen siendo la única fuente
de verdad del área. No hay un `ModoManager2`, ni un `SelectorModoNuevo`, ni un
segundo almacén de modo, ni un sistema de navegación paralelo.

---

## 13.1 La pregunta de la primera vez

### El problema que resuelve

La Fase 0 metió dos áreas delante de alguien que no sabe que hay dos, y lo
resolvió con una tira permanente en las seis pantallas. Funcionaba, pero tiene
dos fallos que solo se ven usándolo:

1. **La tira está siempre ahí.** 38dp que se pagan en cada pantalla por algo
   que se cambia una vez cada meses, y que además se puede tocar por error.
2. **Nadie ha decidido nada.** Quien instala la app por primera vez entra en
   facturación sin que nadie le haya preguntado, y si lo que necesita son
   préstamos no tiene ni una pista de que existieran. El área se cambió sola.

La Fase 0.1 invierte las dos cosas: la pregunta pasa al **primer arranque** y la
tira desaparece.

### La regla, en una línea

> Sin área guardada → se pregunta. Con área guardada → se entra y no se vuelve a
> preguntar nunca más.

### Dónde vive la decisión

Tres piezas, y solo tres:

```
AlmacenDeModo.modoGuardado(): ModoDiarix?     ← "eligió" vs "no eligió"
SelectorDeModo.haceFaltaElegirModo(modo)      ← la regla, sola
SeleccionDeModoActivity                       ← la pantalla de la pregunta
```

**`modoGuardado()` es lo nuevo y es lo que hace todo el trabajo.** Antes, la Fase
0 solo tenía `modoActual()`, que devuelve `INICIAL` (es decir, FACTURACIÓN)
cuando no hay nada guardado, "porque siempre hay que abrir en alguna parte". Ese
comodín es correcto para arrancar y **fatal para preguntar**, porque no puede
distinguir dos estados que aquí son distintos:

| Estado | `modoActual()` | `modoGuardado()` | Se pregunta |
|---|---|---|---|
| Nunca ha entrado | `FACTURACIÓN` | `null` | **Sí** |
| Eligió Facturación | `FACTURACIÓN` | `FACTURACION` | No |
| Eligió Préstamos | `PRESTAMOS` | `PRESTAMOS` | No |
| Clave corrupta | `FACTURACIÓN` | `null` | **Sí** |

La última fila es deliberada: una clave que no se puede leer es un dato que no
se sabe, y no saber no es lo mismo que saber. Se vuelve a preguntar una vez y
se sanea al elegir.

**`haceFaltaElegirModo(modoGuardado)` es una función pura**, sin `Activity`, sin
`Context` y sin red. Por eso la regla "preguntar una vez y solo una" se
comprueba en tests de verdad y no solo opens en un emulador.

### Cómo entra y cómo se guarda

```
LoginActivity.abrirPrincipal()
   └─ haceFaltaElegirModo(AlmacenDeModo(this).modoGuardado())
        ├─ true  → abrirSeleccionDeModo(this)      ← pantalla completa
        └─ false → SelectorDeModo.abrirInicio(this) ← al área, directo
```

**Y lo mismo en `MainActivity`**, que es el `LAUNCHER`. Es el detalle que hace
que esto funcione: si solo se comprobara en el login, quien ya tiene sesión
guardada y reabre la app desde el icono se saltaría la pregunta.

### Por qué una pantalla completa y no un diálogo

Un `AlertDialog` encima del login se puede cerrar con "atrás". Quien entra por
primera vez en DiariX no sabe todavía qué está eligiendo, y una caja de diálogo
cancelable encima del login es exactamente cómo alguien entra a la app sin
decidir nada — y entonces nunca vuelve a ver la pregunta, porque ya tiene el
área por defecto guardada en cuanto hace cualquier cosa. El resultado sería un
usuario en el área equivocada que nunca se le pregunta.

Por eso `SeleccionDeModoActivity` es una `Activity` a pantalla completa:

- Sin botón de cancelar.
- El "atrás" se intercepta con un aviso: "Elige un área para empezar" (y **no**
  se sale de la app).
- Se abre con `CLEAR_TASK | NEW_TASK`, así que no queda debajo la pantalla desde
  la que se partió.

### El detalle que rompía el "solo una vez"

`SelectorDeModo.cambiarA(modo)` hace un guardado condicional: si el modo elegido
es el que ya está vigente, no hace nada, porque "ya estás ahí".

En el primer arranque el vigente es `INICIAL`, que **es** FACTURACIÓN. Así que
alguien que elige "Facturación" en la pantalla inicial pasaba por `cambiarA`,
no se guardaba nada, y la app le volvía a preguntar en cada inicio. Para
siempre.

La solución es `elegirYEntrar(activity, modo)`, que **siempre** guarda, y a la
que `cambiarA` delega. Un solo camino para guardar y entrar, con dos formas de
llamarse según si el área puede cambiar o no.

### Las dos tarjetas

```
┌─────────────────────────────────────┐
│  ¿QUÉ QUIERES GESTIONAR?            │
│  DiariX tiene dos áreas de trabajo. │
│  Elige en la que vas a trabajar hoy.│
├─────────────────────────────────────┤
│  ▢ FACTURACIÓN                      │
│    Registra y administra tus        │
│    facturas y movimientos de pago.  │
│                                     │
│  ▢ PRÉSTAMOS                        │
│    Administra clientes, préstamos,  │
│    cuotas y cobros.                 │
├─────────────────────────────────────┤
│  El área de Préstamos está en        │
│  construcción. Podrás volver a       │
│  cambiar cuando quieras.            │
└─────────────────────────────────────┘
```

La última línea es importante y es una decisión, no un descuido: se entra a un
área sin módulos, así que hay que decirlo antes, no descubrirlo después. En
Ayuda y en Acerca de se repite con más detalle.

### El aviso de que Préstamos está en construcción

Se muestra en la propia pantalla de selección, **no** en un diálogo aparte
después de elegir. Un aviso que aparece cuando ya estás dentro del área es un
aviso que se lee tarde; aquí se lee cuando todavía se puede decidir.

---

## 13.2 La tira: fuera, y por qué se fue

**Qué se borró:**

| Qué | Dónde |
|---|---|
| `res/layout/view_strip_modo.xml` | El archivo entero |
| `<include layout="@layout/view_strip_modo" />` | Las **seis** pantallas de las dos áreas |
| `SelectorDeModo.enlazar(...)` | El método, y sus seis llamadas e imports |
| `mostrar()` | Renombrado a `mostrarComoDialogo()` para que el nombre diga lo que hace |
| Botón "Ir a Facturación" | Layout y código de Préstamos |

De los `<include>` quedó solo un comentario en cada layout que dice **por qué**
no hay tira y dónde está ahora el cambio. Un layout sin tira y sin explicación
es un layout del que el siguiente que lo lea le parece un bug y lo vuelve a poner.

**Por qué se fue, y no se ocultó:** una tira de 38dp que se puede tocar cambia el
área vigente por accidente, desde cualquier pantalla. El área es el contexto de
todo lo que se ve; cambiarla tiene que ser un acto deliberado en un sitio
concreto, no un gesto accidental disponible en todas partes.

---

## 13.3 Configuración, por secciones

### Antes

Cuatro botones del mismo tamaño en tarjetas sin jerarquía: "Seleccionar QR",
"Exportar respaldo", "Importar respaldo" y "Cerrar sesión". Cuatro acciones
visualmente idénticas, sin ninguna pista de cuál se usa a diario y cuál deshace
el trabajo del usuario. Y el único indicador de que "Cerrar sesión" es distinto
de "cambiar un QR" era... que estaba más abajo.

### Ahora

```
┌──────────────────────────────────────┐
│  Perfil                             │
│  ┌────────────────────────────────┐  │
│  │ (AM)  Ana María Torres      ›  │  │
│  │       [CUENTA DE GOOGLE]        │  │
│  │       ana@ejemplo.co            │  │
│  │       300 123 4567              │  │
│  │       Ver y editar perfil     › │  │
│  └────────────────────────────────┘  │
├──────────────────────────────────────┤
│  Cuenta                             │
│  ┌────────────────────────────────┐  │
│  │▌ Modo de DiariX        [Cambiar]│  │
│  │▌ FACTURACIÓN                   │  │
│  │  Puedes cambiar el área de     │  │
│  │  trabajo en cualquier momento. │  │
│  ├────────────────────────────────┤  │
│  │  Activa en este dispositivo     │  │
│  ├────────────────────────────────┤  │
│  │  Cerrar sesión en todos los     │  │
│  │  dispositivos               ›  │  │
│  └────────────────────────────────┘  │
├──────────────────────────────────────┤
│  Comprobantes                       │
│    Código QR de los comprobantes ›  │
├──────────────────────────────────────┤
│  Tus datos                          │
│    Exportar respaldo             ›  │
│    Importar respaldo             ›  │
├──────────────────────────────────────┤
│  Información                        │
│    Ayuda                         ›  │
│    Acerca de DiariX             ›  │
│    Versión 1.0.0 (1)                │
├──────────────────────────────────────┤
│      [  Cerrar sesión  ]            │  ← rojo #D93A3A
│      Volverás a la pantalla de      │
│      acceso al cerrar la sesión.   │
└──────────────────────────────────────┘
```

Cada sección tiene su título con `style="@style/TituloDeSeccion"` y sus filas
separadas por `style="@style/SeparadorDeFila"`. Se reutilizan los colores, las
tarjetas y los radios que ya tenía la app: **no se inventó lenguaje visual**.

**Sobre la barra de color de "Modo de DiariX":** es el mismo verde `#0AA87A` y
el mismo azul `#2B6ED4` de la Fase 0, con los mismos fondos
(`bg_modo_facturacion`, `bg_modo_prestamos`). El color repite lo que dice el
texto, para que se sepa en qué área se está sin tener que leer, y para que el
cambio de área se vea como algo de otra categoría que leer un dato.

### El cambio de área, y por qué "atrás" no vuelve

Tocar "Cambiar" abre `SelectorDeModo.mostrarComoDialogo(this)`, que es el mismo
diálogo de siempre, con las dos áreas y la vigente marcada. Al elegir:

```
elegirYEntrar(activity, modo)
   ├─ AlmacenDeModo(activity).guardar(modo)     ← siempre, incluso si es la misma
   └─ abrirInicio(activity, modo)                ← MAIN | CLEAR_TASK
```

El `CLEAR_TASK` es la Fase 0, no la Fase 0.1, y sigue siendo la razón de que
funcione: borra la pila entera, así que **Configuración desaparece de ella** y
el "atrás" desde el área nueva no puede devolver a la pantalla del área
antigua. No hizo falta nada nuevo para conseguirlo; hizo falta quitar los otros
sitios desde los que se podía cambiar de área.

### Las tres cosas que la pantalla ya tenía y no se tocaron

El QR, la exportación y la importación de respaldo se llevan tal cual: mismos
`ActivityResultContracts`, mismos diálogos de progreso, mismos mensajes, misma
validación previa del ZIP. Solo cambian dónde están los controles y a qué
sección pertenecen. La lógica de facturación no se tocó.

---

## 13.4 El perfil: solo lo que es verdad

### La regla

> Un campo editable que el servidor no acepta no se pone.

Lo contrario es un fallo que no se detecta solo: la pantalla cambia, la persona
se va tranquila y al volver a entrar su nombre es el viejo.

### Lo que se ve

| Dato | De dónde sale | ¿Editable? |
|---|---|---|
| Avatar (iniciales) | `GET /usuarios/yo` → `nombreCompleto` | **No.** Se calcula, no se guarda |
| Nombre | `nombreCompleto` | **Sí** |
| Correo | `email` | **No.** Identidad de la cuenta |
| "Cuenta de Google" | `SessionManager.esCuentaDeGoogle()` | — (es un dato, no un ajuste) |
| Teléfono | `telefono` | **Sí** |
| Rol, empresa, plan | `GET /usuarios/yo` | **No.** Los fija el servidor |

**Solo dos campos editables: nombre y teléfono.** Son exactamente los que acepta
`PATCH /usuarios/yo`.

### El correo

`ActualizarPerfilDto` del backend no tiene campo de correo, así que mandarlo lo
ignora en silencio. Con Google es además la identidad que Google firmó, y el
backend la reescribe desde Google si cambia allí: un `EditText` de correo
"editable" produciría un cambio aparente que el servidor revierte a los cinco
segundos.

Por eso **el correo no está dentro de un campo de texto, ni de solo lectura**. En
`EditarPerfilActivity` está en un bloque aparte, con su explicación al lado. Un
`EditText` deshabilitado parece editable y la primera vez que alguien lo toca
piensa que la app está rota.

### El avatar

`users` **no tiene columna de avatar** y el backend no conserva la foto de
Google. No hay nada que editar, así que no hay nada que ofrecer. El círculo de
Configuración son las iniciales del nombre real (`PerfilEditable.iniciales`):
dos palabras → la primera letra de las dos primeras (`"Ana María Torres"` → `AM`),
una palabra → sus dos primeras letras (`"Ana"` → `AN"`), y sin nombre → vacío,
para que quien lo pinte decida (se pinta `?`) en vez de inventar una letra.

### La marca de "Cuenta de Google"

**El backend no dice si una cuenta está vinculada a Google.** Ni `GET /usuarios/yo`
ni el token: `users` solo guarda el `googleSub` y no se expone. Suponerlo por el
dominio del correo sería inventar (`@gmail.com` no es una cuenta de Google
Laboratorio).

Lo que sí lo sabe con certeza es el propio inicio de sesión: si se apretó
"Continuar con Google", la cuenta es de Google. Así que se guarda en
`TokensDeSesion.conGoogle`, que viaja **dentro del mismo blob cifrado** que los
tokens.

Vivir ahí, y no en una variable en memoria, por un motivo concreto: si solo
estuviera en memoria, la etiqueta desaparecería de Configuración en cuanto el
sistema matara el proceso — que es justo lo que pasa cuando el móvil lleva un
rato en la mesa — y el usuario pensaría que su cuenta se ha desligado de Google.

Y en `SessionManager.refrescar()` hay que acordarse de copiarla:

```kotlin
almacen.guardar(renovados.tokens.copy(conGoogle = actuales.conGoogle))
```

Sin ese `copy`, renovar la sesión (cada 15 minutos) borraría la marca y la
etiqueta desaparecería a mitad de tarde sin que nadie tocara nada. Hay un test
justo para eso.

Al cerrar sesión la marca se borra con los tokens, porque es un dato de **esta
sesión**, no de la cuenta: quien entre después con contraseña no debe ver una
etiqueta de Google que no le corresponde.

### Guardar no es una edición de interfaz

`EditarPerfilActivity` solo manda lo que ha **cambiado de verdad**: un campo
intacto viaja como `null` ("no lo toques") y uno que la persona ha dejado vacío
viaja como `""` ("vacíalo"). Es la diferencia entre no querer cambiar el teléfono
y querer quitarlo, y por eso se comparan antes de decidir qué enviar.

Y al guardar **se vuelve a leer `GET /usuarios/yo`** en lugar de suponer que el
cambio entró. No es manía: el `PATCH` del backend responde con los nombres de
la COLUMNA (`fullName`, `phone`), no con los del DTO (`nombreCompleto`,
`telefono`), porque es lo que devuelve Prisma con su `select`. Reconstruir la
ficha desde esa respuesta dejaría el nombre vacío.

`SessionManager.actualizarMiPerfil` además refresca `perfilActual.nombre`, para
que el resto de la app no siga enseñando el nombre viejo. Si no, quien acaba de
corregir su nombre en Configuración lo vería mal en Inicio, creería que el
guardado no funcionó y volvería a corregirlo indefinidamente.

---

## 13.5 Las pantallas nuevas

Las tres van en `core/cuenta/`, no en `modulos/facturacion/`, porque son **de
las dos áreas**. Editar perfil, Ayuda y Acerca de son las mismas dentro de
Facturación y dentro de Préstamos: si dependieran del área, habría que mantener
dos copias que se desincronizan en cuanto se corrige un texto en una.

| Pantalla | Qué es |
|---|---|
| `EditarPerfilActivity` | Los dos campos editables, con sus límites (160 y 32) y el correo en lectura |
| `AyudaActivity` | Dónde está cada cosa. **No** es un sistema de soporte |
| `AcercaDeActivity` | Qué es DiariX, la versión real y una nota de privacidad |

Las cuatro están declaradas en el manifiesto con `exported="false"`, y hay tests
que lo comprueban: una pantalla no declarada crashea al abrirla, y una exportada
que no debería serlo pone un formulario con tus datos a un toque de distancia de
otra aplicación.

### La versión es real

`txtVersion` se rellena en código con `BuildConfig.VERSION_NAME` y
`BuildConfig.VERSION_CODE`. Nunca escrito a mano: un `"1.0.1"` en un texto es una
mentira en cuanto se publica la 1.0.2, y el número que ve alguien al abrir esta
pantalla es exactamente el que se le dice a soporte.

Hay hasta un test de que **el layout no trae el número escrito**: si lo trajera,
empezaría a discrepar de `BuildConfig` con la siguiente versión.

### Lo que NO hay, y por qué

| Ausente | Por qué |
|---|---|
| Sección **Apariencia** | DiariX no tiene sistema de temas. Un interruptor de "tema oscuro" sería un control que se puede tocar y no hace nada |
| Sección **Notificaciones** | La app no notifica nada |
| **Avatar** editable | No hay columna en `users` ni foto de Google conservada |
| **Correo** editable | `ActualizarPerfilDto` no lo acepta |
| **Rol / empresa / plan** editables | Los fija el servidor (invitación de empresa, CLI del primer administrador) |
| Enlaces a **términos / privacidad / contacto** | No existen: no hay sitio web ni textos escritos. Un enlace a una política que nadie ha escrito es un problema legal y de confianza a la vez |
| **Chat / tickets / correo de soporte** | No hay nada detrás |
| **Licencias de terceros** | Hay que generarlas desde las dependencias reales (`aboutLibraries` o un `NOTICE` de build), no transcribirlas a mano |

La ausencia de Apariencia y Notificaciones **no es una omisión**: hay un test
(`ConfiguracionTest`) que falla si algún día aparece un `switchTema` o una
`filaNotificaciones`, para que quien lo añada tenga que decidir de verdad qué
implementa detrás en vez de dejar un interruptor suelto.

Y Ayuda termina diciéndolo explícitamente:

> Aquí solo hay ayuda de uso. DiariX todavía no tiene un canal de soporte.

Enseñar los límites de la ayuda es parte de la ayuda: quien no encuentra su
respuesta tiene que poder distinguir "DiariX no lo tiene" de "no me lo he
encontrado".

### Cerrar sesión

Botón rojo `#D93A3A`, fuera de las tarjetas de secciones, al final, con un texto
de ayuda debajo. Y con su propio diálogo:

```
┌──────────────────────────────────────┐
│  ¿Quieres cerrar tu sesión?          │
│                                      │
│  Deberás iniciar sesión nuevamente    │
│  para acceder a DiariX.              │
│                                      │
│      [Cancelar]     [Cerrar sesión]  │
└──────────────────────────────────────┘
```

El comportamiento de cierre **no se cambió**: sigue llamando a `/auth/logout`
(revoca el refresh en el servidor) y, pase lo que pase con la red, borra los
tokens locales y vuelve al login.

**"Cerrar sesión en todos los dispositivos"** sí es una función real, no un
invento: `POST /auth/logout-todos` existe en el backend y devuelve
`sesionesRevocadas`. Dice de qué va ("esto revoca las sesiones de tus otros
teléfonos; este sigue dentro") antes de hacerlo, porque quien toca eso desde un
teléfono que ha perdido otro está resolviendo un susto y necesita saber si va a
perder también el acceso desde aquí.

### El estado de la sesión

Una línea de texto, **sin chevron**, porque no lleva a ninguna parte de la app
donde arreglarlo. Traduce el rol del backend a cómo se llama esa persona
(`ACCOUNTANT` → "contador", `DRIVER` → "conductor"...) y un rol desconocido se
devuelve **tal cual**: si el servidor añade un quinto rol y la app no lo conoce,
hay que **verlo** en la pantalla (así se nota en las pruebas) y no leer
"usuario", que haría creer que todo el mundo es un usuario normal.

---

## 13.6 Préstamos: cómo se sale de ahí

La Fase 0 dio a Préstamos un botón "Ir a Facturación". Con la Fase 0.1, ese
botón era un **segundo** sitio para cambiar de área, y tener dos sitios para
cambiar de área es el problema que se vino a arreglar. Así que se retiró.

Pero Préstamos se quedaba entonces con un menú de **una sola pestaña** y sin
forma de llegar a Configuración, ni al perfil, ni a cerrar sesión. **Eso sí es un
agujero.** La solución:

```
┌──────────────────────────────────────┐
│  DIARIX                        ⚙     │  ← btnAbrirConfiguracion
│                                      │     44dp · @drawable/ic_settings
│  ┌────────────────────────────────┐  │     tint #5A6182
│  │  PRÉSTAMOS                     │  │
│  └────────────────────────────────┘  │
└──────────────────────────────────────┘
```

El engranaje abre `ConfiguracionActivity`, la misma de siempre. Desde ahí se
cambia de área, se ve el perfil y se cierra sesión.

**`BottomNavigator.sincronizarPestana` tuvo que adaptarse a esto:**
`nav_configuracion` no está en el menú de Préstamos, así que la función
seleccionaba... nada, dejando el menú sin pestaña marcada. Ahora, si el id que se
pide no está en el menú inflado, selecciona **la primera pestaña del área** en
lugar de dejar el menú a medias.

---

## 13.7 El fallo de Estadísticas

No era un fallo nuevo, era un fallo de la Fase 0, y merece su párrafo porque
enseña algo sobre cómo se compila Android.

**Síntoma:** entrar en Estadísticas cerraba la app.

**Causa:** la Fase 0 movió las cuatro gráficas de `com.example.totaldiaria.ui.graficas`
a `com.example.totaldiaria.modulos.facturacion.ui`, pero
`activity_estadisticas.xml` siguió nombrando el paquete viejo en cuatro
`android:...` attributes.

**Por qué el build estaba en verde:** AAPT2 **no valida** los nombres de clase
de las vistas personalizadas. Solo los empaqueta. El error sale en tiempo de
ejecución, al inflar: `ClassNotFoundException` → la app se cierra. O sea, un
fallo de runtime en un APK que compila, y que ninguna prueba de JVM detecta
porque no hay ninguna Activity.

**Arreglo:** cuatro FQN corregidos a
`com.example.totaldiaria.modulos.facturacion.ui.Grafica{Lineas,Barras,Dona,BarrasHorizontales}`.

**Lo importante es que no vuelva a pasar por el mismo camino.** Se añadió
`recursos/LayoutsInflablesTest.kt`, que **infla TODOS** los layouts de
`res/layout` con Robolectric, y que además **comprueba su propia lista contra el
directorio**: si alguien añade un layout y no lo mete en la lista, el test
falla. Es decir, no hay forma de añadir una pantalla sin que se pruebe.

Comprobado que el test detecta el fallo: revirtiendo temporalmente los cuatro
FQN, fallan 2 tests con `java.lang.ClassNotFoundException`; al restaurarlos,
todo en verde.

Se hizo un barrido de todo `res/` buscando referencias al paquete viejo:
**solo existían esas cuatro.**

---

## 13.8 Archivos

### Nuevos (14)

| Archivo | Para qué |
|---|---|
| `core/modo/SeleccionDeModoActivity.kt` | "¿Qué quieres gestionar?" a pantalla completa |
| `core/cuenta/EditarPerfilActivity.kt` | Los dos campos editables |
| `core/cuenta/AyudaActivity.kt` | Ayuda de uso |
| `core/cuenta/AcercaDeActivity.kt` | Qué es DiariX y su versión real |
| `network/UsuariosApi.kt` | `GET`/`PATCH /usuarios/yo`, `POST /auth/logout-todos` |
| `res/layout/activity_seleccion_modo.xml` | La pantalla de la pregunta |
| `res/layout/activity_editar_perfil.xml` | El formulario de perfil |
| `res/layout/activity_ayuda.xml` | La ayuda |
| `res/layout/activity_acerca_de.xml` | Acerca de |
| `res/drawable/ic_facturacion.xml` | Ícono de la tarjeta de Facturación |
| `res/drawable/ic_chevron.xml`<br>`res/drawable/ic_ayuda.xml`<br>`res/drawable/ic_info.xml`<br>`res/drawable/ic_seguridad.xml` | Íconos de las filas |
| `res/drawable/bg_avatar.xml`<br>`res/drawable/bg_etiqueta_config.xml` | El círculo de iniciales y la etiqueta |
| `recursos/LayoutsInflablesTest.kt`<br>`recursos/ConfiguracionTest.kt`<br>`core/modo/EntradaAlAreaTest.kt`<br>`core/cuenta/PerfilEditableTest.kt`<br>`network/UsuariosApiTest.kt`<br>`sesion/PerfilEnSesionTest.kt` | Las pruebas de la Fase 0.1 |

### Modificados (13)

| Archivo | Cambio |
|---|---|
| `modulos/facturacion/ui/ConfiguracionActivity.kt` | **Reescrito** por secciones. Conserva el QR y el respaldo |
| `modulos/prestamos/ui/PrestamosInicioActivity.kt` | Engranaje a Configuración; fuera el botón "Ir a Facturación" |
| `core/modo/SelectorDeModo.kt` | `haceFaltaElegirModo`, `elegirYEntrar`; fuera `enlazar` |
| `core/modo/AlmacenDeModo.kt` | `modoGuardado()` |
| `core/navegacion/BottomNavigator.kt` | `sincronizarPestana` tolera un id que no está en su menú |
| `Diarix.kt` | `UsuariosApi` entra en el grafo |
| `sesion/ModeloSesion.kt` | `PerfilEditable`; `TokensDeSesion.conGoogle` |
| `sesion/SessionManager.kt` | `miPerfil`, `actualizarMiPerfil`, `cerrarSesionEnTodosLosDispositivos`, `esCuentaDeGoogle` |
| `sesion/TokenStore.kt` | Persiste `conGoogle` |
| `network/ApiCliente.kt` | `patchJson` |
| `AndroidManifest.xml` | Cuatro Activities nuevas, todas `exported="false"` |
| `res/layout/activity_configuracion.xml` | **Reescrito** por secciones |
| `res/layout/activity_estadisticas.xml` | Los cuatro FQN de las gráficas **arreglados** |
| `res/layout/activity_main.xml`<br>`activity_historial.xml`<br>`activity_registros.xml` | Fuera el `<include>` de la tira |
| `res/values/strings.xml` · `themes.xml` | Textos y estilos nuevos |

### Borrados (2)

| Archivo | Por qué |
|---|---|
| `res/layout/view_strip_modo.xml` | La tira ya no está en ninguna pantalla |
| `SelectorDeModo.enlazar` | Su único trabajo era pintar la tira |

---

## 13.9 Pruebas

### Antes y después

```
LÍNEA BASE (antes de la Fase 0)     77 tests, 0 fallos, 0 errores, 1 omitido
FASE 0                             90 tests, 0 fallos, 0 errores, 1 omitido
FASE 0.1 (+ LayoutsInflablesTest)   93 tests, 0 fallos, 0 errores, 1 omitido
FASE 0.1 (esta fase)               148 tests, 0 fallos, 0 errores, 1 omitido
```

El omitido es el mismo de siempre: `AlmacenSeguroTest`, que depende del Android
Keystore. **No se desactivó ni se borró ningún test.** Los de la Fase 0 siguen
intactos bajo el paquete nuevo.

### Las 58 pruebas nuevas de la Fase 0.1

| Archivo | N | Qué fija |
|---|---|---|
| `core/modo/EntradaAlAreaTest.kt` | 15 | La regla de preguntar: sin área se pregunta; con **cualquier** área no; elegir el área por defecto **también** cuenta como haber elegido; una clave ilegible vuelve a preguntar pero nunca deja la app sin pantalla; las cuatro pantallas nuevas están declaradas y no son `exported`; las de cuenta son de las dos áreas |
| `core/cuenta/PerfilEditableTest.kt` | 13 | Lo **único** editable es nombre y teléfono; `null` ≠ `""` (no tocar ≠ vaciar); sin nombre no se inventan iniciales; un perfil vacío no trae datos inventados |
| `network/UsuariosApiTest.kt` | 12 | Rutas y verbos reales: `GET`/`PATCH /usuarios/yo`, `POST /auth/logout-todos`; `null` no viaja en el cuerpo; **el correo nunca se manda**; tras el PATCH se vuelve a leer con un `GET`; un 422 se propaga como `ErrorDiarix` |
| `sesion/PerfilEnSesionTest.kt` | 7 | La marca de Google sobrevive al refresh, **no** sobrevive a cerrar sesión, un token viejo sin la marca sigue siendo sesión válida, guardar el nombre actualiza el perfil de sesión |
| `recursos/ConfiguracionTest.kt` | 8 | Las secciones existen; **no hay** controles de apariencia ni de notificaciones; cerrar sesión está **después** de Información y es **rojo**; la versión **no** viene escrita en el layout; hay menú inferior |
| `recursos/LayoutsInflablesTest.kt` | 3 | **Todos** los layouts se pueden inflar, y la lista está completa contra el directorio |

El `UsuariosApiTest` corre bajo Robolectric a propósito: `ApiCliente` usa
`org.json`, y en un test de JVM normal los métodos de `JSONObject` vienen de un
stub del SDK que **lanza excepción**, así que el archivo entero fallaría con
"Method toString in org.json.JSONObject not mocked", que dice más del
andamiaje de pruebas que del código que se quiere probar.

### Un fallo real que encontró una prueba

`UsuariosApiTest` destapó un bug que ya estaba en el código: `JSONObject.optString`
devuelve **la cadena `"null"`** cuando el valor del JSON es un nulo
(`JSONObject.NULL`), no un nulo de Kotlin. Con `"telefono": null` — que es
literalmente lo que devuelve el backend para un usuario sin teléfono— la app
habría pintado el texto **null** en el campo del teléfono, y ese texto habría
volvido al servidor en el siguiente `PATCH`.

Se corrigió con `UsuariosApi.texto(clave)`, que mira si el valor es `JSONObject.NULL`
o está ausente en vez de comprobar que el texto no sea "null" (esa segunda forma
convertiría de paso el nombre "null" en ausencia de dato).

### Lo que los tests no cubren

Se necesita un dispositivo o emulador, y queda pendiente de revisión manual:

- Que no se corte ningún texto ni se salga ningún botón en un teléfono de 5".
- Que el teclado no tape "Guardar" en la pantalla de perfil.
- Que el `CLEAR_TASK` al cambiar de área se comporte bien con "atrás" de verdad.
- Que la "+ de Configuración y el engranaje de Préstamos se vean bien.

---

## 13.10 Decisiones de la Fase 0.1

| Decisión | Por qué |
|---|---|
| Preguntar al entrar, y **solo** al entrar | Una pregunta que aparece en cada arranque hace que la gente cierre la app, y eso no tiene arreglo técnico |
| Pantalla completa y no diálogo | Un diálogo cancelable encima del login es cómo se entra sin decidir nada |
| `modoGuardado()` además de `modoActual()` | `modoActual()` devuelve un área siempre; no puede distinguir "eligió" de "nunca eligió" |
| `haceFaltaElegirModo` como función pura | La regla del "solo una vez" tiene que poder comprobarse sin Activities |
| `elegirYEntrar` **siempre** guarda | `cambiarA` sale sin hacer nada si el área es la misma, y en el primer arranque la misma es Facturación: alguien que eligiera Facturación no se guardaría nunca |
| Cambiar de área desde `MainActivity` también | Es el `LAUNCHER`: si solo se comprobara en el login, quien reabre con sesión se saltaría la pregunta |
| Clave corrupta → preguntar de nuevo | Un dato que no se puede leer no es un dato. Se pregunta una vez y se sanea al elegir |
| "En construcción" **antes** de entrar a Préstamos | Un aviso después de entrar es un aviso leído tarde |
| Retirar la tira en vez de ocultarla | 38dp que se pueden tocar en cada pantalla cambian el contexto de trabajo por accidente |
| Retirar también "Ir a Facturación" | Dos sitios para cambiar de área es el problema que se vino a arreglar |
| El engranaje en Préstamos | Sin él, Préstamos no llegaba a su propio perfil ni a cerrar sesión |
| Perfil solo con lo que el backend acepta | Un campo que el servidor ignora cambia la pantalla y no cambia nada: es peor que no ofrecerlo |
| El correo fuera de un `EditText` | Aunque fuera de solo lectura parece editable, y el backend lo reescribe desde Google |
| La marca de Google en el blob cifrado | Si viviera en memoria, desaparecería al matar el proceso y el usuario creería que se desligó |
| `copy(conGoogle)` en el refresh | Sin él, la etiqueta se va cada 15 minutos sin que nadie toque nada |
| Se relee con un `GET` tras el `PATCH` | El PATCH responde con nombres de columna (`fullName`), no del DTO: parsearlo dejaría el nombre vacío |
| `null` ≠ `""` en el PATCH | "No lo toques" y "vacíalo" son cosas distintas, y borrar un dato no debería costar cambiar otro |
| Apariencia y Notificaciones **ausentes** | No hay sistema de temas ni notificaciones. Un control que no hace nada enseña a desconfiar de los que sí |
| Ayuda sin canal de soporte | Un "Escríbenos" que no llega a nadie es peor que no poner nada |
| Sin enlaces a términos ni privacidad | No existen. Un enlace a una página que no está escrita es un problema legal y de confianza |
| `core/cuenta/` y no `modulos/facturacion/` | Perfil, Ayuda y Acerca de son las mismas en las dos áreas; dos copias se desincronizan |
| `ConfiguracionActivity` se queda en `modulos/facturacion/` | Es una pestaña de Facturación con lógica de facturación (QR, respaldo) dentro. Moverla es rehacer la Fase 0 |
| La versión de `BuildConfig` | Un número escrito a mano miente en cuanto se publica la siguiente |

---

## 13.11 Riesgos

| Riesgo | Gravedad | Estado |
|---|---|---|
| **Nada se ha probado en un dispositivo real** | **Media** | **Pendiente.** Ver la lista manual de 13.9 y de abajo |
| `LayoutsInflablesTest` necesita alguien que se acuerde de añadir el layout a su lista | Baja | Se reduce a cero: el test **comprueba su propia lista** contra `res/layout` |
| La marca de Google depende de un dato local, no del backend | Baja | Es lo único que hay. El día que el backend exponga si la cuenta está vinculada, se cambia la fuente y el resto igual |
| `ConfiguracionActivity` vive en `modulos.facturacion` pero la usan las dos áreas | Baja | Heredado de la Fase 0. Moverlo es una reorganización, no una fase |
| `core.modo` sigue conociendo clases concretas de los dos módulos | Baja | Heredado de la Fase 0, aislado en `ModoDiarix` |
| El rol se traduce con cuatro `if` | Muy baja | Son cuatro valores de un enum cerrado. Un rol nuevo se ve en pantalla en vez de esconderse |
| La validación de límites (160 / 32) está en la app | Baja | El backend también valida; la app valida antes para poder avisar con un mensaje que se entienda |

### Recorrido manual pendiente

El orden que hay que seguir en un móvil real:

1. Entrar con Google → aparece "¿Qué quieres gestionar?".
2. Elegir **Facturación** → entra directo en Inicio.
3. Cerrar la app y reabrirla → **no** vuelve a preguntar.
4. Abrir Configuración → ver el perfil con nombre, correo y "Cuenta de Google".
5. Editar el perfil: cambiar el nombre, guardar, comprobar que Inicio enseña el nuevo.
6. Intentar cambiar el **correo** → comprobar que no hay ningún campo para eso.
7. Configuración → Modo de DiariX → Cambiar → **Préstamos**.
8. Pulsar **atrás** → comprobar que **no** vuelve a Facturación.
9. Desde Préstamos, el engranaje → Configuración → Cambiar → Facturación.
10. Atrás → no vuelve a Préstamos.
11. Ayuda y Acerca de: leerlos enteros, mirar la versión.
12. Cerrar sesión → confirmar → comprobar que vuelve al login.
13. Entrar otra vez → elegir Préstamos esta vez → comprobar que repregunta y entra allí.

---

## 13.12 Lo que sigue pendiente

| Falta | Por qué | Dónde se resuelve |
|---|---|---|
| **Avatar de perfil** | `users` no tiene columna de foto y el backend no conserva la de Google | Backend: columna + `GET /usuarios/yo` la devuelve; luego la app sustituye el círculo de iniciales |
| **Foto de Google** | Habría que pedirla con `idToken` y guardarla | `LoginActivity` al autenticar con Google |
| Cambiar el correo | El backend no lo acepta | Backend: campo en `ActualizarPerfilDto` y sincronización con Google |
| Editar empresa / rol | Los fija el servidor | Backend: endpoints de administración de empresa (existen para el admin, no para el usuario) |
| Apariencia | No hay sistema de temas | Backend: ninguno. App: sistema de temas + interruptor real |
| Notificaciones | La app no notifica | App: notificaciones de cierres y vencimientos + interruptor real |
| Licencias de terceros | Hay que generarlas, no transcribirlas | Build: `aboutLibraries` o un `NOTICE` generado |
| Canal de soporte | No hay nada detrás | Empresa: decisión de negocio |
| Préstamos con módulos | Es la Fase 1 | `modulos/prestamos/` |

---

## 13.13 Validación final de la Fase 0.1

| # | Comprobación | Resultado |
|---|---|---|
| 1 | La app compila | ✅ `clean :app:assembleDebug` BUILD SUCCESSFUL |
| 2 | Los tests existentes siguen pasando | ✅ 77 de antes, 0 fallos; 148 en total |
| 3 | No se desactivó ni borró ningún test | ✅ El omitido es el preexistente de Keystore |
| 4 | Sin área guardada se pregunta | ✅ `haceFaltaElegirModo(null)` + `EntradaAlAreaTest` |
| 5 | Con área guardada no se vuelve a preguntar | ✅ Las dos áreas + el área por defecto |
| 6 | La elección se guarda de verdad (sobrevive al cierre) | ✅ Se relee del almacén, no de memoria |
| 7 | El "atrás" no devuelve al área anterior | ✅ `MAIN \| CLEAR_TASK` desde `elegirYEntrar` |
| 8 | Una clave ilegible no deja la app sin pantalla | ✅ Pregunta de nuevo y `modoActual()` sigue dando inicio |
| 9 | La tira desapareció de las seis pantallas | ✅ Archivo borrado, 6 `<include>` fuera, 6 `enlazar` fuera |
| 10 | El cambio de área vive **solo** en Configuración | ✅ Fuera también "Ir a Facturación" |
| 11 | Préstamos tiene salida | ✅ Engranaje a Configuración |
| 12 | Configuración está organizada por secciones | ✅ 5 secciones + cerrar sesión fuera |
| 13 | El perfil solo muestra datos reales | ✅ Iniciales del nombre real, nada inventado |
| 14 | Solo se edita lo que el backend acepta | ✅ `nombreCompleto` y `telefono`, y nada más |
| 15 | El correo no es editable y se explica por qué | ✅ Bloque de lectura con la explicación al lado |
| 16 | "Cuenta de Google" solo si la cuenta ES de Google | ✅ Viene del método de acceso, no del dominio del correo |
| 17 | La versión es la de verdad | ✅ `BuildConfig.VERSION_NAME` + `VERSION_CODE` |
| 18 | No hay controles muertos | ✅ Test que falla si aparece un `switchTema` o `filaNotificaciones` |
| 19 | Cerrar sesión se distingue y se confirma | ✅ Rojo, fuera de secciones, diálogo con el texto pedido |
| 20 | El cierre de sesión sigue funcionando | ✅ `/auth/logout` + borrado local + vuelta al login |
| 21 | Facturación sigue intacta | ✅ Cero cambios de lógica; QR y respaldo conservados tal cual |
| 22 | No se inventó ningún endpoint | ✅ Solo `GET`/`PATCH /usuarios/yo` y `POST /auth/logout-todos`, que existen |
| 23 | No se implementó nada de la Fase 1 | ✅ Préstamos sigue siendo el andamiaje vacío |
| 24 | La base de datos no se tocó | ✅ Sin migraciones, sin DDL, sin `DROP` |
| 25 | Estadísticas ya no cierra la app | ✅ 4 FQN corregidos + test que infla todos los layouts |

**Pendiente de revisión humana:** todo el recorrido manual de 13.11, y en
particular el punto 8 (el "atrás" después de cambiar de área), que es el único
que depende de cómo Android gestione la pila en un dispositivo de verdad.

---

## 13.14 Para la Fase 1

Sin cambios en el orden. Lo que la Fase 0.1 deja preparado:

1. **Instalar el APK de debug** y hacer el recorrido de 13.11. Es lo único que
   falta para dar por buena la fase.
2. `modulos/clientes/` con `modelo`, `repositorio`, `servicio`, `ui`. Su tabla
   en SQLite y en Prisma, con `tenant_id` en el servidor.
3. `modulos/prestamos/` con su calendario de cuotas.

Lo primero que hará un módulo nuevo, siempre: agregar su pestaña al menú de **su**
área y su `Activity` al manifiesto. Si necesita un área nueva, una fila más en
`ModoDiarix`.

Y una regla que la Fase 0.1 deja establecida: **si un control no tiene algo
real detrás, no entra en la pantalla.** Los tests `ConfiguracionTest` y
`LayoutsInflablesTest` la vigilan.
