package com.coffemod.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Repository(context: Context) {

    private val helper = DbHelper(context.applicationContext)

    private val db get() = helper.writableDatabase

    // ── группы ────────────────────────────────────────────────────────────────

    fun groups(): List<Group> {
        val result = mutableListOf<Group>()
        db.rawQuery("SELECT id, name, sort FROM groups ORDER BY sort, name", null).use { c ->
            while (c.moveToNext()) result += Group(c.getLong(0), c.getString(1), c.getInt(2))
        }
        return result
    }

    fun addGroup(name: String): Long {
        val values = ContentValues().apply {
            put("name", name.trim())
            put("sort", (groups().maxOfOrNull { it.sort } ?: 0) + 1)
        }
        return db.insertOrThrow("groups", null, values)
    }

    fun renameGroup(id: Long, name: String) {
        db.update("groups", ContentValues().apply { put("name", name.trim()) }, "id = ?", arrayOf(id.toString()))
    }

    fun deleteGroup(id: Long): Boolean {
        val count = db.rawQuery("SELECT COUNT(*) FROM products WHERE group_id = ?", arrayOf(id.toString())).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        if (count > 0) return false
        db.delete("groups", "id = ?", arrayOf(id.toString()))
        return true
    }

    fun productCount(groupId: Long): Int =
        db.rawQuery("SELECT COUNT(*) FROM products WHERE group_id = ? AND archived = 0", arrayOf(groupId.toString())).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

    // ── товары ────────────────────────────────────────────────────────────────

    fun products(query: String = "", groupId: Long? = null): List<Product> {
        val where = StringBuilder("p.archived = 0")
        val args = mutableListOf<String>()
        if (query.isNotBlank()) {
            where.append(" AND p.name LIKE ?")
            args += "%${query.trim()}%"
        }
        if (groupId != null) {
            where.append(" AND p.group_id = ?")
            args += groupId.toString()
        }
        val result = mutableListOf<Product>()
        db.rawQuery(
            "SELECT p.id, p.name, p.group_id, p.unit, p.min_stock, p.stock, p.expires_on, p.note " +
                "FROM products p WHERE $where ORDER BY p.name COLLATE NOCASE",
            args.toTypedArray(),
        ).use { c ->
            while (c.moveToNext()) result += c.toProduct()
        }
        return result
    }

    fun product(id: Long): Product? {
        db.rawQuery(
            "SELECT id, name, group_id, unit, min_stock, stock, expires_on, note FROM products WHERE id = ?",
            arrayOf(id.toString()),
        ).use { c ->
            return if (c.moveToNext()) c.toProduct() else null
        }
    }

    fun save(product: Product, codes: List<String>): Long {
        val values = ContentValues().apply {
            put("name", product.name.trim())
            put("group_id", product.groupId)
            put("unit", product.unit)
            put("min_stock", product.minStock)
            put("stock", product.stock)
            put("expires_on", product.expiresOn)
            put("note", product.note)
            put("archived", if (product.archived) 1 else 0)
        }
        val id = if (product.id == 0L) {
            values.put("created_at", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            db.insertOrThrow("products", null, values)
        } else {
            db.update("products", values, "id = ?", arrayOf(product.id.toString()))
            product.id
        }
        db.delete("barcodes", "product_id = ?", arrayOf(id.toString()))
        codes.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { code ->
            db.insertWithOnConflict("barcodes", null, ContentValues().apply {
                put("product_id", id)
                put("code", code)
            }, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
        }
        return id
    }

    fun deleteProduct(id: Long) {
        db.beginTransaction()
        try {
            db.delete("barcodes", "product_id = ?", arrayOf(id.toString()))
            db.delete("movements", "product_id = ?", arrayOf(id.toString()))
            db.delete("session_items", "product_id = ?", arrayOf(id.toString()))
            db.delete("products", "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun codesFor(productId: Long): List<String> {
        val result = mutableListOf<String>()
        db.rawQuery("SELECT code FROM barcodes WHERE product_id = ? ORDER BY code", arrayOf(productId.toString())).use { c ->
            while (c.moveToNext()) result += c.getString(0)
        }
        return result
    }

    fun productByCode(code: String): Product? {
        db.rawQuery(
            "SELECT p.id, p.name, p.group_id, p.unit, p.min_stock, p.stock, p.expires_on, p.note " +
                "FROM products p JOIN barcodes b ON b.product_id = p.id WHERE b.code = ? LIMIT 1",
            arrayOf(code.trim()),
        ).use { c ->
            return if (c.moveToNext()) c.toProduct() else null
        }
    }

    private fun Cursor.toProduct() = Product(
        id = getLong(0),
        name = getString(1),
        groupId = if (isNull(2)) null else getLong(2),
        unit = getString(3),
        minStock = getDouble(4),
        stock = getDouble(5),
        expiresOn = if (isNull(6)) null else getString(6),
        note = getString(7),
    )

    // ── движения: приход и расход ────────────────────────────────────────────

    fun addMovement(productId: Long, kind: String, qty: Double, note: String = "", onDate: String? = null) {
        db.beginTransaction()
        try {
            db.insertOrThrow("movements", null, ContentValues().apply {
                put("product_id", productId)
                put("kind", kind)
                put("qty", qty)
                put("on_date", onDate ?: java.time.LocalDate.now().toString())
                put("note", note)
            })
            val delta = if (kind == "in") qty else -qty
            db.execSQL("UPDATE products SET stock = stock + ? WHERE id = ?", arrayOf<Any?>(delta, productId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun movements(limit: Int = 30): List<MovementRow> {
        val result = mutableListOf<MovementRow>()
        db.rawQuery(
            "SELECT m.id, m.product_id, p.name, p.unit, m.kind, m.qty, m.on_date, m.note " +
                "FROM movements m JOIN products p ON p.id = m.product_id ORDER BY m.id DESC LIMIT $limit",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                result += MovementRow(
                    id = c.getLong(0),
                    productId = c.getLong(1),
                    productName = c.getString(2),
                    unit = c.getString(3),
                    kind = c.getString(4),
                    qty = c.getDouble(5),
                    onDate = c.getString(6),
                    note = c.getString(7),
                )
            }
        }
        return result
    }

    // ── инвентаризация ───────────────────────────────────────────────────────

    fun startSession(): Long {
        val id = db.insertOrThrow("sessions", null, ContentValues().apply {
            put("started_at", java.time.LocalDateTime.now().toString())
            put("status", "draft")
        })
        return id
    }

    fun draftSession(): SessionRow? {
        db.rawQuery(
            "SELECT id, started_at, status FROM sessions WHERE status = 'draft' ORDER BY id DESC LIMIT 1",
            null,
        ).use { c ->
            return if (c.moveToNext()) SessionRow(c.getLong(0), c.getString(1), c.getString(2)) else null
        }
    }

    fun sessionItems(sessionId: Long): List<SessionItemRow> {
        val result = mutableListOf<SessionItemRow>()
        db.rawQuery(
            "SELECT si.product_id, p.name, p.unit, si.expected, si.actual " +
                "FROM session_items si JOIN products p ON p.id = si.product_id " +
                "WHERE si.session_id = ? ORDER BY p.name COLLATE NOCASE",
            arrayOf(sessionId.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                result += SessionItemRow(
                    productId = c.getLong(0),
                    name = c.getString(1),
                    unit = c.getString(2),
                    expected = c.getDouble(3),
                    actual = c.getDouble(4),
                )
            }
        }
        return result
    }

    fun setSessionItem(sessionId: Long, productId: Long, actual: Double) {
        val expected = product(productId)?.stock ?: 0.0
        val updated = db.update(
            "session_items",
            ContentValues().apply { put("actual", actual); put("expected", expected) },
            "session_id = ? AND product_id = ?",
            arrayOf(sessionId.toString(), productId.toString()),
        )
        if (updated == 0) {
            db.insertOrThrow("session_items", null, ContentValues().apply {
                put("session_id", sessionId)
                put("product_id", productId)
                put("expected", expected)
                put("actual", actual)
            })
        }
    }

    fun finishSession(sessionId: Long) {
        db.beginTransaction()
        try {
            val items = sessionItems(sessionId)
            items.forEach { item ->
                db.execSQL(
                    "UPDATE products SET stock = ? WHERE id = ?",
                    arrayOf<Any?>(item.actual, item.productId),
                )
            }
            db.update(
                "sessions",
                ContentValues().apply {
                    put("status", "done")
                    put("finished_at", java.time.LocalDateTime.now().toString())
                },
                "id = ?",
                arrayOf(sessionId.toString()),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun countProducts(): Int =
        db.rawQuery("SELECT COUNT(*) FROM products WHERE archived = 0", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun sessionsDone(limit: Int = 50): List<SessionRow> {
        val result = mutableListOf<SessionRow>()
        db.rawQuery(
            "SELECT id, started_at, status FROM sessions WHERE status = 'done' ORDER BY id DESC LIMIT $limit",
            null,
        ).use { c ->
            while (c.moveToNext()) result += SessionRow(c.getLong(0), c.getString(1), c.getString(2))
        }
        return result
    }

    /** Первый штрихкод товара; если кодов нет — создаёт внутренний INT-код и возвращает его. */
    fun ensureCode(productId: Long): String {
        val existing = codesFor(productId)
        if (existing.isNotEmpty()) return existing.first()
        val code = "INT-$productId"
        db.insertWithOnConflict("barcodes", null, ContentValues().apply {
            put("product_id", productId)
            put("code", code)
        }, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
        return code
    }

    // ── алиасы кассовых названий ─────────────────────────────────────────────

    fun aliases(): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        db.rawQuery("SELECT name, product_id FROM aliases", null).use { c ->
            while (c.moveToNext()) result[c.getString(0)] = c.getLong(1)
        }
        return result
    }

    fun setAlias(rawName: String, productId: Long) {
        db.insertWithOnConflict(
            "aliases",
            null,
            ContentValues().apply {
                put("name", rawName.trim().lowercase())
                put("product_id", productId)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }
}
