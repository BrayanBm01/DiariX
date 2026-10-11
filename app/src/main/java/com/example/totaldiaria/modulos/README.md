# DIARIX · Módulos (Fase 0)

Este archivo es la guía de la carpeta `modulos/`. Léelo antes de crear
cualquier archivo dentro.

---

## Qué es un "módulo" aquí

Un módulo es **una carpeta con prefijo propio**, no un módulo de Gradle.

La app sigue siendo **un solo módulo de Gradle** (`:app`) y lo sigue siendo a
propósito. Partir el build en varios módulos de Gradle resolvería un problema
que no tenemos —compilar más rápido, aislar dependencias de terceros entre
equipos— y costaría una capa de configuración por cada módulo antes de
escribir la primera línea de código. Cuando haga falta, se hace; no ahora.

Lo que sí existe desde esta fase es la separación **lógica**: cada módulo es
dueño de su modelo, su lógica y sus pantallas, y no mete las manos en los
datos de otro. Esa separación es la que cuesta caro retrofitar más adelante.

---

## Por qué `modulos/` y no en la raíz

El árbol quedó así:

```
com.example.totaldiaria/
├── core/                  lo transversal de la app (área, navegación)
├── modulos/               los módulos de negocio
│   ├── facturacion/       ← el DiariX actual, sin cambios de comportamiento
│   └── prestamos/         ← el sistema nuevo, en construcción
├── network/               HTTP: transporte y errores
└── sesion/                autenticación, tokens, perfil
```

- **`core/`** — lo que no es de ningún módulo: en qué área se está, cómo se
  navega. Si algo sirve a los dos módulos, va aquí y no en ninguno de los dos.
- **`modulos/`** — los módulos de negocio. Cada uno con sus capas.
- **`network/`** y **`sesion/`** — infraestructura transversal que ya existía
  y no se tocó.

---

## Las capas de un módulo

Adaptadas a lo que esta app **de verdad** usa (SQLite con `SQLiteOpenHelper`
yOkHttp a mano; **no** hay Room, ni Hilt, ni Retrofit, ni Compose):

| Carpeta | Qué va dentro | Equivalente al del documento de requisitos |
|---|---|---|
| `modelo/` | Entidades locales: `Factura`, `PapeleraItem`… | `modelo/` |
| `dto/` | Formatos de entrada/salida de la API | `dto/` |
| `repositorio/` | Acceso a la base de datos local | `repositorio/` |
| `servicio/` | Lógica de negocio y servicios de la API | `servicio/` + `servicio_implementado/` |
| `sincronizacion/` | Cola offline: solo si el módulo tiene una | — |
| `adaptador/` | `RecyclerView.Adapter` | — |
| `ui/` | Activities, layouts, diálogos, formatos, gráficas | `ui/` |

Dos diferencias con el organigrama del documento de requisitos, y por qué:

1. **`servicio/` en vez de `servicio/` + `servicio_implementado/`.** El código
   actual no usa puertos y adaptadores: los servicios son clases concretas.
   Partirlos en dos sería reescribir lo que funciona a cambio de una forma.
2. **`ui/` en vez de `páginas/` + `componentes/`.** Aquí las pantallas son
   Activities con layout XML, no páginas. La separación UI/contenido ya está
   hecha por los `*Service`.

`dto/` no se creó porque hoy no hay nada que poner dentro. Se crea el día que
un módulo tenga su primera llamada a la API.

**Las carpetas vacías no se crean.** Se crean con su primer archivo. Un
`README.md` en una carpeta que no tiene código es ruido que miente sobre lo que
existe.

---

## La regla que no se rompe

> Un módulo usa el **servicio** de otro módulo. Nunca su **repositorio**.

El repositorio es el detalle de cómo un módulo guarda sus cosas. Si el módulo
de préstamos leyera tablas del módulo de facturación a través de su
repositorio, los dos módulos dejarían de poder moverse por separado el día que
uno de los dos necesite cambiar de base de datos.

Cuando un módulo necesite algo de otro, primero se pregunta si de verdad lo
necesita. Si lo necesita, es un servicio del otro módulo y se expone así.

---

## Qué módulo es cuál

| Módulo | Área | Estado |
|---|---|---|
| `facturacion/` | FACTURACIÓN | Funcionando, sin cambios de comportamiento |
| `prestamos/` | PRÉSTAMOS | Solo la pantalla de inicio |

Y los que encore no existen, con el nombre que deben tener cuando lleguen:

`clientes/` · `prestamos/` · `cuotas/` · `cobros/` · `pagos/` ·
`dashboard/` · `portal/` · `suscripciones/`

Cada uno con sus cinco capas. Ninguno necesita existir antes de tener código.

---

## Cómo se añade un módulo nuevo

1. Carpeta `modulos/<nombre>/` con sus capas.
2. Un `ModoDiarix` por área, **solo si es un área nueva**. Si el módulo nuevo
   pertenece a un área que ya existe (por ejemplo `cuotas`, que es de
   préstamos), no se toca `ModoDiarix`: se agrega su pestaña al menú de ese
   área.
3. Su `Activity` en el `AndroidManifest`.
4. Su `layout` con el mismo encabezado de las demás pantallas del área.
5. Sus destinos en `BottomNavigator`, en el mapa del área que le corresponde.
6. Su `layout` de menú, si necesita pestaña, y el caso en `MenuDeModo`.
7. Su `layout` en la lista de `recursos/LayoutsInflablesTest`.

Los puntos 2 y 6 son los que hacen que la separación se sostenga. Son dos, y
están escritos para que no se puedan saltarse por descuido.

**Sobre la tira de área (Fase 0.1): ya no existe.** Una pantalla nueva **no**
lleva tira. El cambio de área vive solo en Configuración → Modo de DiariX, y esa
es la razón de que una pantalla nueva no tenga que hacer nada para poder
cambiarse de área: está en un sitio, y en un sitio se queda.

El punto 7 parece burocrático y es lo que impide repetir el fallo que hizo que
Estadísticas cerrara la app: un layout que solo se rompe al inflarlo, con el
build en verde. Ese test infla **todos** los layouts y además comprueba su
propia lista contra el directorio, así que no se puede añadir una pantalla sin
que se pruebe.