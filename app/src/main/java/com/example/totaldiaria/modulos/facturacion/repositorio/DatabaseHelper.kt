package com.example.totaldiaria.modulos.facturacion.repositorio

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.totaldiaria.core.cuenta.CuentaLocal
import java.util.UUID

/**
 * Base SQLite privada de una cuenta.
 *
 * El esquema sigue siendo el de siempre (versión 9): lo único que cambia es el
 * NOMBRE del archivo. Así dos cuentas independientes no comparten tablas ni el
 * índice global de `clientUid`, sin necesidad de migrar columnas.
 */
class DatabaseHelper(
    context: Context,
    ownerId: String = CuentaLocal.id(),
) : SQLiteOpenHelper(context, nombrePara(ownerId), null, 9) {

    companion object {
        fun nombrePara(ownerId: String): String = CuentaLocal.nombreBase(ownerId)
    }


    override fun onCreate(db: SQLiteDatabase) {

        db.execSQL(
            """
            CREATE TABLE facturas(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                numeroFactura TEXT,
                efectivo REAL,
                transferencia REAL,
                fecha TEXT,
                estado TEXT DEFAULT 'ACTIVA',
                comprobanteUri TEXT,
                clientUid TEXT,
                estadoSincronizacion TEXT NOT NULL DEFAULT 'PENDIENTE',
                servidorId TEXT,
                intentos INTEGER NOT NULL DEFAULT 0,
                ultimoError TEXT,
                comprobanteSubido INTEGER NOT NULL DEFAULT 0,
                comprobanteIntentos INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE registros(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fecha TEXT,
                cantidad INTEGER,
                efectivo REAL,
                transferencia REAL,
                total REAL,
                cantidadEfectivo INTEGER DEFAULT 0,
                cantidadTransferencia INTEGER DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE papelera(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fecha TEXT,
                cantidad INTEGER,
                efectivo REAL,
                transferencia REAL,
                total REAL,
                cantidadEfectivo INTEGER DEFAULT 0,
                cantidadTransferencia INTEGER DEFAULT 0
            )
            """.trimIndent()
        )

        // La clave de idempotencia es única: repetir un envío jamás duplica.
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_facturas_clientUid
            ON facturas (clientUid) WHERE clientUid IS NOT NULL
            """.trimIndent()
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {

        if (oldVersion < 2) {

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS papelera(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    fecha TEXT,
                    cantidad INTEGER,
                    efectivo REAL,
                    transferencia REAL,
                    total REAL
                )
                """.trimIndent()
            )
        }

        if (oldVersion < 3) {

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS registros(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    fecha TEXT,
                    cantidad INTEGER,
                    efectivo REAL,
                    transferencia REAL,
                    total REAL
                )
                """.trimIndent()
            )
        }

        if (oldVersion < 4) {

            try {
                db.execSQL(
                    """
                    ALTER TABLE facturas
                    ADD COLUMN numeroFactura TEXT
                    """.trimIndent()
                )
            } catch (_: Exception) {
            }
        }

        if (oldVersion < 5) {

            try {
                db.execSQL(
                    """
                    ALTER TABLE facturas
                    ADD COLUMN estado TEXT DEFAULT 'ACTIVA'
                    """.trimIndent()
                )
            } catch (_: Exception) {
            }
        }

        if (oldVersion < 6) {

            try {
                db.execSQL(
                    """
                    ALTER TABLE registros
                    ADD COLUMN cantidadEfectivo INTEGER DEFAULT 0
                    """.trimIndent()
                )
            } catch (_: Exception) {
            }

            try {
                db.execSQL(
                    """
                    ALTER TABLE registros
                    ADD COLUMN cantidadTransferencia INTEGER DEFAULT 0
                    """.trimIndent()
                )
            } catch (_: Exception) {
            }

            try {
                db.execSQL(
                    """
                    ALTER TABLE papelera
                    ADD COLUMN cantidadEfectivo INTEGER DEFAULT 0
                    """.trimIndent()
                )
            } catch (_: Exception) {
            }

            try {
                db.execSQL(
                    """
                    ALTER TABLE papelera
                    ADD COLUMN cantidadTransferencia INTEGER DEFAULT 0
                    """.trimIndent()
                )
            } catch (_: Exception) {
            }
        }

        // VERSIÓN 7: comprobante de transferencia
        if (oldVersion < 7) {

            try {
                db.execSQL(
                    """
                    ALTER TABLE facturas
                    ADD COLUMN comprobanteUri TEXT
                    """.trimIndent()
                )
            } catch (_: Exception) {
            }
        }

        // VERSIÓN 8 (Fase 4): sincronización con DiariX.
        //  - clientUid: clave de idempotencia que genera el móvil.
        //  - estadoSincronizacion/servidorId/intentos/ultimoError: cola local.
        if (oldVersion < 8) {

            try {
                db.execSQL(
                    """
                    ALTER TABLE facturas
                    ADD COLUMN clientUid TEXT
                    """.trimIndent()
                )
            } catch (_: Exception) {
            }

            try {
                db.execSQL(
                    """
                    ALTER TABLE facturas
                    ADD COLUMN estadoSincronizacion TEXT NOT NULL DEFAULT 'PENDIENTE'
                    """.trimIndent()
                )
            } catch (_: Exception) {
            }

            try {
                db.execSQL(
                    """
                    ALTER TABLE facturas
                    ADD COLUMN servidorId TEXT
                    """.trimIndent()
                )
            } catch (_: Exception) {
            }

            try {
                db.execSQL(
                    """
                    ALTER TABLE facturas
                    ADD COLUMN intentos INTEGER NOT NULL DEFAULT 0
                    """.trimIndent()
                )
            } catch (_: Exception) {
            }

            try {
                db.execSQL(
                    """
                    ALTER TABLE facturas
                    ADD COLUMN ultimoError TEXT
                    """.trimIndent()
                )
            } catch (_: Exception) {
            }

            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_facturas_clientUid
                ON facturas (clientUid) WHERE clientUid IS NOT NULL
                """.trimIndent()
            )

            // Las facturas HISTÓRICAS (ya ARCHIVADA) nunca se suben: la
            // sincronización empieza con Fase 4 y el histórico local ya quedó
            // consolidado en `registros`. Solo las activas de este momento
            // reciben clientUid para sincronizarse.
            asignarUuidsAFacturasActivas(db)
        }

        // VERSIÓN 9: subida del comprobante al servidor.
        //
        // El móvil guardaba la foto en `filesDir`, pero nunca la enviaba:
        // el panel web no podía verla. Ahora se sube justo después de que el
        // servidor confirma la factura y se marca para no repetirla.
        if (oldVersion < 9) {

            try {
                db.execSQL(
                    """
                    ALTER TABLE facturas
                    ADD COLUMN comprobanteSubido INTEGER NOT NULL DEFAULT 0
                    """.trimIndent()
                )
            } catch (_: Exception) {
            }

            try {
                db.execSQL(
                    """
                    ALTER TABLE facturas
                    ADD COLUMN comprobanteIntentos INTEGER NOT NULL DEFAULT 0
                    """.trimIndent()
                )
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Da un `clientUid` (UUID real, como exige el backend) a las facturas
     * activas que aún no lo tienen, tras migrar a la versión 8.
     */
    private fun asignarUuidsAFacturasActivas(db: SQLiteDatabase) {
        val ids = mutableListOf<Long>()

        db.rawQuery(
            "SELECT id FROM facturas WHERE estado = 'ACTIVA' AND clientUid IS NULL",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                ids.add(cursor.getLong(0))
            }
        }

        for (id in ids) {
            db.execSQL(
                "UPDATE facturas SET clientUid = ? WHERE id = ?",
                arrayOf(UUID.randomUUID().toString(), id.toString())
            )
        }
    }
}