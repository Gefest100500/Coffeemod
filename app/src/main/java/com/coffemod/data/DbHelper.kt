package com.coffemod.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE groups (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                sort INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE products (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                group_id INTEGER,
                unit TEXT NOT NULL DEFAULT 'шт',
                min_stock REAL NOT NULL DEFAULT 0,
                stock REAL NOT NULL DEFAULT 0,
                expires_on TEXT,
                note TEXT NOT NULL DEFAULT '',
                archived INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE barcodes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER NOT NULL,
                code TEXT NOT NULL UNIQUE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE movements (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER NOT NULL,
                kind TEXT NOT NULL,
                qty REAL NOT NULL,
                on_date TEXT NOT NULL,
                note TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                started_at TEXT NOT NULL,
                finished_at TEXT,
                status TEXT NOT NULL DEFAULT 'draft',
                note TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE session_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER NOT NULL,
                product_id INTEGER NOT NULL,
                expected REAL NOT NULL DEFAULT 0,
                actual REAL NOT NULL DEFAULT 0,
                note TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_products_group ON products(group_id)")
        db.execSQL("CREATE INDEX idx_barcodes_product ON barcodes(product_id)")
        db.execSQL("CREATE INDEX idx_movements_product ON movements(product_id)")
        db.execSQL(
            """
            CREATE TABLE aliases (
                name TEXT PRIMARY KEY,
                product_id INTEGER NOT NULL
            )
            """.trimIndent()
        )

        val defaults = listOf("Кофе", "Молочка", "Сиропы", "Напитки", "Кондитерка", "Расходники", "Упаковка")
        defaults.forEachIndexed { index, name ->
            db.execSQL("INSERT INTO groups(name, sort) VALUES(?, ?)", arrayOf<Any?>(name, index))
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS aliases (
                    name TEXT PRIMARY KEY,
                    product_id INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    companion object {
        const val DB_NAME = "coffemod.db"
        const val DB_VERSION = 2
    }
}
