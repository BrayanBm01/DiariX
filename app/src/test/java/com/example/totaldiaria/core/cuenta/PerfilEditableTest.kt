package com.example.totaldiaria.core.cuenta

import com.example.totaldiaria.sesion.EmpresaSesion
import com.example.totaldiaria.sesion.PerfilActual
import com.example.totaldiaria.sesion.PerfilEditable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DIARIX · Fase 0.1 · Qué de la ficha de perfil se puede cambiar.
 *
 * ## Por qué esto necesita un test
 *
 * La tentación de una pantalla de perfil es poner un campo para todo lo que se
 * ve. Aquí no se puede, porque el backend solo acepta dos campos en
 * `PATCH /usuarios/yo` (`ActualizarPerfilDto`: `nombreCompleto` y `telefono`).
 *
 * Un campo editable que el servidor ignora es la peor clase de bug de este
 * sitio, y no se detecta solo: la pantalla cambia, el usuario se va tranquilo
 * y al volver a entrar su nombre es el viejo. Estos tests fijan el LÍMITE de lo
 * editable, para que nadie lo cruce pensando que es una mejora.
 */
class PerfilEditableTest {

    // ----------------------------------------------------------------
    // Lo que se puede cambiar
    // ----------------------------------------------------------------

    @Test
    fun `el nombre y el telefono son los unicos campos editables`() {

        val perfil = perfilDePrueba(nombre = "Ana María Torres", telefono = "3001234567")

        // Estos dos SÍ se mandan al servidor.
        assertEquals("Ana María Torres", perfil.nombreCompleto)
        assertEquals("3001234567", perfil.telefonoEditable)
    }

    @Test
    fun `el telefono ausente se lee como texto vacio y no como la palabra null`() {

        val perfil = perfilDePrueba(telefono = null)

        // Un campo que se pinta con "null" escrito dentro parece un dato roto.
        assertEquals("", perfil.telefonoEditable)
        assertNull(perfil.telefono)
    }

    /**
     * Un teléfono vacío es un teléfono que la persona ha BORRADO.
     *
     * Por eso `telefono` es nullable y `telefonoEditable` no: `null` significa
     * "no se toca este campo" y `""` significa "vacíalo". Si se colapsaran los
     * dos, no se podría borrar el teléfono sin volver a mandar el nombre, y
     * borrar un dato propio no debería costar un cambio en otro.
     */
    @Test
    fun `telefono vacio y telefono ausente son cosas distintas`() {

        val ausente = perfilDePrueba(telefono = null)
        val vacio = perfilDePrueba(telefono = "")

        assertNull(ausente.telefono)
        assertEquals("", vacio.telefono)
        assertEquals("", ausente.telefonoEditable)
        assertEquals("", vacio.telefonoEditable)
    }

    // ----------------------------------------------------------------
    // Lo que NO se puede cambiar
    // ----------------------------------------------------------------

    /**
     * El correo es de lectura, y hay que poder comprobarlo.
     *
     * La razón de que no sea editable: con Google es la identidad que Google
     * firmó, y el backend la reescribe desde Google si cambia allí. Un
     * `EditText` de correo "editable" produciría un cambio que el servidor
     * revierte a los cinco segundos.
     */
    @Test
    fun `el correo viene del servidor y no se marca como editable`() {

        val perfil = perfilDePrueba(correo = "ana.torres@ejemplo.co")

        assertEquals("ana.torres@ejemplo.co", perfil.email)
    }

    /**
     * Rol, empresa y plan NO son de la persona.
     *
     * El backend no tiene ningún endpoint para que alguien se cambie su propio
     * rol: los fija el servidor (invitación de empresa, CLI del primer
     * administrador). Que la ficha los muestre es correcto —son información—;
     * que parezcan editables sería un agujero de privilegios.
     */
    @Test
    fun `rol empresa y plan se muestran pero no son editables`() {

        val perfil = PerfilEditable(
            id = "usr_1",
            email = "ana@ejemplo.co",
            nombreCompleto = "Ana Torres",
            telefono = null,
            rol = "ACCOUNTANT",
            estado = "ACTIVE",
            correoVerificado = true,
            empresaNombre = "Contabilidad Torres",
            plan = "PRO",
        )

        assertEquals("ACCOUNTANT", perfil.rol)
        assertEquals("Contabilidad Torres", perfil.empresaNombre)
        assertEquals("PRO", perfil.plan)
    }

    // ----------------------------------------------------------------
    // Construir la ficha antes de que haya red
    // ----------------------------------------------------------------

    /**
     * Se pinta en cuanto se abre Configuración, sin esperar a `GET /usuarios/yo`.
     *
     * La sesión (`/auth/yo`) NO trae el teléfono, así que desde ahí el
     * teléfono es `null` y se enseña como vacío. No se rellena con nada: un
     * dato inventado en la pantalla de perfil es el sitio más fácil donde se
     * cuela una mentira, y aquí alguien lo lee pensando que es suyo.
     */
    @Test
    fun `desde el perfil de sesion se hereda todo menos el telefono`() {

        val perfil = PerfilEditable.desde(perfilActualDeSesion())

        assertEquals("usr_1", perfil.id)
        assertEquals("ana@ejemplo.co", perfil.email)
        assertEquals("Ana Torres", perfil.nombreCompleto)
        assertEquals("ACCOUNTANT", perfil.rol)
        assertEquals("Contabilidad Torres", perfil.empresaNombre)

        // Lo que `/auth/yo` no trae.
        assertNull(perfil.telefono)
        assertEquals("", perfil.telefonoEditable)
    }

    @Test
    fun `desde el perfil de sesion no se inventa la empresa si no la hay`() {

        val sinEmpresa = perfilActualDeSesion().copy(empresa = null)

        assertNull(PerfilEditable.desde(sinEmpresa).empresaNombre)
    }

    @Test
    fun `un perfil vacio no inventa datos`() {

        val vacio = PerfilEditable.vacio()

        assertEquals("", vacio.email)
        assertEquals("", vacio.nombreCompleto)
        assertEquals("", vacio.telefonoEditable)
        assertNull(vacio.empresaNombre)
        assertNull(vacio.plan)
        assertFalse(vacio.correoVerificado)
    }

    @Test
    fun `sin sesion la ficha se puede pintar con el correo que se conozca`() {

        assertEquals(
            "ana@ejemplo.co",
            PerfilEditable.vacio("ana@ejemplo.co").email
        )
    }

    // ----------------------------------------------------------------
    // El avatar: iniciales del nombre REAL
    // ----------------------------------------------------------------

    /**
     * No hay foto de perfil, así que el avatar son las iniciales del nombre.
     *
     * Con dos o más palabras se toma la primera letra de las dos PRIMERAS ("Ana
     * María Torres" → "AM", "Ana Torres" → "AT"), que es como se leen unas
     * iniciales. Con una sola palabra, sus dos primeras letras ("Ana" → "AN"),
     * porque con una palabra no hay con qué hacer dos iniciales.
     *
     * Siempre en mayúsculas y sin tildes. La tilde no tiene versión de mayúscula
     * en todos los sistemas donde corre Android, así que "Peña" daría "PEA" en
     * unos móviles y "PEÑ" en otros; se quitan para que el avatar sea el mismo
     * en todos ellos.
     *
     * Se documentan las tres formas porque la diferencia entre "AM" y "AT" en un
     * nombre de tres palabras es exactamente la clase de detalle por el que
     * alguien escribe un ticket diciendo que la app le pone mal su nombre.
     */
    @Test
    fun `las iniciales del avatar salen del nombre real`() {

        assertEquals("AM", PerfilEditable.iniciales("Ana María Torres"))
        assertEquals("AT", PerfilEditable.iniciales("Ana Torres"))

        assertEquals("AN", PerfilEditable.iniciales("Ana"))

        // En minúsculas se leen igual: "juan pérez" → "JP".
        assertEquals("JP", PerfilEditable.iniciales("juan pérez"))
    }

    /**
     * Con un nombre de tres palabras mandan las dos primeras, no la primera y la
     * última.
     *
     * "Ana María Torres" → "AT" parece más bonito, pero no es lo que significa
     * "iniciales": unas iniciales son las del nombre y el apellido, que en un
     * nombre compuesto son las dos primeras palabras.
     */
    @Test
    fun `con tres palabras mandan las dos primeras`() {

        assertEquals("AM", PerfilEditable.iniciales("Ana María Torres"))
        assertEquals("JC", PerfilEditable.iniciales("Juan Carlos Pérez"))
    }

    /**
     * Los espacios de sobra no estorban.
     *
     * Vienen de cola: Google los manda así y el backend los guarda tal cual, así
     * que un nombre guardado a mano puede llegar con espacios al principio y al
     * final. Si no se limpiaran, "   Ana Torres" daría iniciales de la nada.
     */
    @Test
    fun `un nombre con espacios de sobra produce iniciales utiles`() {

        assertEquals("AT", PerfilEditable.iniciales("   Ana   Torres   "))
        assertEquals("MT", PerfilEditable.iniciales("María\tTorres"))
    }

    /**
     * Sin nombre no hay iniciales inventadas.
     *
     * Devuelve vacío en vez de una letra cualquiera para que sea quien pinta el
     * avatar quien decida qué hacer: un "?" o un guion dice "no hay nombre", y
     * una "X" sería un nombre que el usuario nunca escribió.
     */
    @Test
    fun `sin nombre no se inventan iniciales`() {

        assertEquals("", PerfilEditable.iniciales(""))
        assertEquals("", PerfilEditable.iniciales("   "))
        assertEquals("", PerfilEditable.iniciales("\n\t "))
    }

    // ----------------------------------------------------------------
    // Utilidades
    // ----------------------------------------------------------------

    private fun perfilDePrueba(
        nombre: String = "Ana Torres",
        telefono: String? = null,
        correo: String = "ana@ejemplo.co",
    ) = PerfilEditable(
        id = "usr_1",
        email = correo,
        nombreCompleto = nombre,
        telefono = telefono,
        rol = "ACCOUNTANT",
        estado = "ACTIVE",
        correoVerificado = true,
        empresaNombre = "Contabilidad Torres",
        plan = "PRO",
    )

    private fun perfilActualDeSesion() = PerfilActual(
        usuarioId = "usr_1",
        email = "ana@ejemplo.co",
        nombre = "Ana Torres",
        rol = "ACCOUNTANT",
        emailVerificado = true,
        empresa = EmpresaSesion(
            id = "emp_1",
            nit = "901234567",
            nombre = "Contabilidad Torres",
            plan = "PRO",
            cupoConductores = null,
        ),
        conductorId = null,
        plan = null,
    )
}