package com.example.totaldiaria.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, "facturas.db", null, 7) {

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
                comprobanteUri TEXT
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
    }
}