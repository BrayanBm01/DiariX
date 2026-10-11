# DIARIX · Fase 2 · Clientes + Facturación

Módulo de **clientes** completo: backend (Prisma + REST + tests) y Android
(listado, búsqueda, formulario crear/editar, borrado), integrado en la
navegación del área de **Facturación** (4 pestañas), sin tocar Préstamos más
allá de su menú congelado.

Fecha: 2026-10-06 · Backend: `Diarix/backend` · Android: `apps/TotalDiaria`

---

## 1. Contrato del API

| Verbo    | Ruta                | Query                       | Rol                     |
| -------- | ------------------- | --------------------------- | ----------------------- |
| `GET`    | `/clientes`         | `pagina`, `porPagina`(≤200), `q`(≤120), `empresaId` (solo ADMIN) | autenticado |
| `GET`    | `/clientes/:id`     | —                           | autenticado |
| `POST`   | `/clientes`         | `?empresaId=` obligatorio para ADMIN | `ACCOUNTANT, ADMIN, DRIVER, USER` |
| `PATCH`  | `/clientes/:id`     | —                           | `ACCOUNTANT, ADMIN, DRIVER, USER` |
| `DELETE` | `/clientes/:id`     | —                           | `ACCOUNTANT, ADMIN` |

- Listado → sobre estándar `{datos[], total, pagina, porPagina, totalPaginas}`.
- Ficha → `{id, empresaId, nombres, apellidos, nombreCompleto,
  tipoDocumento, numeroDocumento, email, telefono, direccion, referencias,
  observaciones, creadoEn, actualizadoEn}`.
- Errores → 400 `VALIDACION`/`ENTRADA_INVALIDA` (con `detalles[]`), 401
  `NO_AUTENTICADO`, 403 `NO_AUTORIZADO`, 404 `NO_ENCONTRADO` (incluido id
  mal formado, nunca 500), 422 `ReglaDeNegocioError`.

## 2. Decisiones de diseño (backend)

- **Modelo Prisma `Client`**: `businessId` nullable + `createdBy` (obligatorio).
  Un usuario individual (sin empresa) registra clientes propios; un usuario de
  empresa los registra del padrón de su empresa. Índices
  `[businessId, apellidos]`, `[businessId, numeroDocumento]`,
  `[createdBy, apellidos]`. Nombre de tabla `clients` (plural, no pluraliza).
- **Borrado definitivo**, no lógico: es lo que ya hacía Conductor y el RF lo
  pide ("eliminación"). El cliente eliminado no aparece ni en el listado ni en
  la auditoría puede reconstruirlo.
- **Sin unicidad de `numeroDocumento`**: el RF-02 no la pide y hacerla crearía
  la primera regla de negocio de un padrón sin negocio detrás. Punto de
  extensión marcado en `ClientesService` para cuando existan los préstamos.
- **NIT del cliente NO se valida con módulo 11**: ese algoritmo valida el NIT
  de la empresa que factura, no el del cliente. Un cliente puede ser un
  extranjero u otra empresa y se deja que el dato pase tal cual.
- **Campos obligatorios**: nombres, apellidos, tipoDocumento, numeroDocumento.
  Opcionales: email, telefono, direccion, referencias, observaciones.
- **Catálogo `TIPOS_DE_DOCUMENTO`**: `CC, CE, NIT, TI, PASAPORTE, RUT`, en
  `clientes.dto.ts`. Android lo duplica en `Cliente.TIPOS_DE_DOCUMENTO` y un
  test (`ClienteTest`) fija que ambos coincidan.
- **Roles**: GET autenticado / POST+PATCH `ACCOUNTANT, ADMIN, DRIVER, USER` /
  DELETE `ACCOUNTANT, ADMIN`. Tocar un cliente ajeno (otra empresa) o un
  cliente del usuario individual desde la empresa → 403 y nada cambia.
- **Auditoría**: `CLIENT_CREATED / CLIENT_UPDATED / CLIENT_DELETED`,
  SIN `numeroDocumento` en la metadata (un documento es un dato sensiblemente
  personal que no pinta nada en el historial de auditoría).
- **`@IsOptional` y `null`**: el DTO acepta `null` y `undefined` en opcionales
  (skip). `ClientesService.texto()` y `obligatorio()` tratan `null` explícito
  para que borrar un campo no produzca un 500 sino un 400 explicable. Cubierto
  con el test `un campo opcional se puede borrar enviando null`.

## 3. Decisiones de diseño (Android)

- **Sin arquitectura nueva**: Activities + XML + HTTP, con `Diarix.enHiloDeFondo`
  + `runOnUiThread` (como las demás pantallas). Sin coroutines, fragments,
  ViewBinding ni Hilt. El token sale de `Diarix.sesion.tokensListos()`.
- **Repositorio único**: `ClientesRepositorio` registrado en `Diarix` (grafo de
  objetos único, mismo `OkHttp` y misma renovación de token que Facturas).
  `ClientesApi` recibe un `ApiCliente` y se abre al `DELETE` (`deleteJson`).
- **`ClientesActivity`**: listado paginado (25 por página) con búsqueda con
  retardo de 400 ms y descarte de respuestas tardías (`versionDeCarga`).
  Estados Loading / Empty / Error / Success; sin conexión muestra Error con
  Reintentar, porque no hay caché local inventada. Paginación con flechas solo
  cuando hay más de una página.
- **`ClienteFormularioActivity`**: un solo formulario para alta y edición
  (enterito cargado con `GET /clientes/:id`), con los 9 campos y tipo de
  documento en Spinner con el catálogo. Validación local SOLO de los 4 campos
  obligatorios con la MISMA regla que el backend (recortar y mirar si queda
  algo); el resto lo valida el servidor y sus `detalles[]` se pintan tal cual.
  El borrado vive aquí (con diálogo de confirmación que muestra el nombre y el
  documento), no en el listado.
- **Navegación**: `menu_facturacion.xml` pasa a 4 pestañas
  `Inicio | Clientes | Facturas | Configuración`; `nav_registros` y
  `nav_estadisticas` se eliminan del menú y del mapa de destinos, y sus
  pantallas pasan a abrirse desde dos tarjetas de acceso en Inicio
  (sección "Más de Facturación"). `RegistrosActivity`/`EstadisticasActivity`
  dejan de poner barra inferior (pantallas secundarias, como EditarPerfil).
- **Sin HTTP en layouts ni SQL en pantallas**: `activity_clientes.xml` no
  contiene URLs y la pantalla no toca `org.json`: el JSON lo arma
  `ClientesApi` y las pantallas reciben `Cliente`/`DatosDeCliente` tipados.

## 4. Qué NO se implementó (y por qué)

- **Regla "no eliminar cliente con préstamos activos"**: no existe la entidad
  Préstamo. Debe implementarse cuando exista, como 422 de negocio en
  `ClientesService.eliminar` (punto de extensión ya marcado).
- **Relación facturas↔clientes**: `Invoice.clientUid` es el UUID de
  idempotencia del móvil, no una FK a `Client`. Si se quiere asociar facturas
  a clientes, es una fase posterior con su propia migración.
- **Caché offline del padrón**: los clientes se leen siempre del servidor.
  Sin conexión, la lista muestra el estado de Error con Reintentar. Meter
  SQLite aquí sería la tercera arquitectura de datos de la app; se documenta
  como pendiente y no se simula.
- **Portal del cliente, dashboard, cobros, súper admin, web**: fases
  posteriores, fuera del alcance.

## 5. Pruebas

- Backend: `test/clientes.e2e-spec.ts` — **29 pruebas** (alta, validaciones,
  paginación, búsqueda, edición, borrado, campos `null`, aislamiento entre
  empresas, roles, ADMIN, auditoría). Suite completa: **429 tests, 23 suites,
  0 fallos**; `tsc --noEmit` limpio.
- Android:
  - `network/ClientesApiTest.kt` — contrato real contra MockWebServer:
    rutas, verbos (incluido DELETE sin cuerpo), sobre paginado, `null` que no
    se convierte en la cadena "null", errores con `detalles`.
  - `modulos/facturacion/modelo/ClienteTest.kt` — catálogo y etiquetas.
  - Congelados actualizados (la navegación cambió por diseño):
    `ModoDiarixTest` (4 pestañas + manifiesto con las 2 Activities nuevas),
    `BarraPrestamosTest` (lista de 5 → 4), `LayoutsInflablesTest` (3 layouts
    nuevos registrados).
- `./gradlew :app:testDebugUnitTest`, lint y `assembleDebug` en verde.

## 6. Pendientes documentados (para fases posteriores)

1. Unidad de préstamos y reglas de bloqueo de borrado.
2. Relación factura → cliente.
3. Almacenamiento local del padrón si el uso sin conexión lo justifica.