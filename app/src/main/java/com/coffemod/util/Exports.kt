package com.coffemod.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.coffemod.data.Repository
import java.io.File
import java.time.LocalDate

private fun escape(value: String): String {
    val needs = value.contains(';') || value.contains('"') || value.contains('\n')
    val escaped = value.replace("\"", "\"\"")
    return if (needs) "\"$escaped\"" else escaped
}

private fun writeCsv(context: Context, name: String, header: List<String>, rows: List<List<String>>): File {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, name)
    val sb = StringBuilder()
    sb.append('\uFEFF')
    sb.append(header.joinToString(";") { escape(it) }).append('\n')
    rows.forEach { row -> sb.append(row.joinToString(";") { escape(it) }).append('\n') }
    file.writeText(sb.toString(), Charsets.UTF_8)
    return file
}

fun exportProductsCsv(context: Context, repo: Repository): File {
    val groups = repo.groups().associate { it.id to it.name }
    val rows = repo.products().map { p ->
        listOf(
            p.name,
            groups[p.groupId] ?: "",
            p.unit,
            p.stock.toString(),
            p.minStock.toString(),
            p.expiresOn ?: "",
            repo.codesFor(p.id).joinToString(","),
        )
    }
    return writeCsv(
        context,
        "coffemod-tovary-${LocalDate.now()}.csv",
        listOf("Название", "Группа", "Ед. изм.", "Остаток", "Мин. остаток", "Срок годности", "Штрихкоды"),
        rows,
    )
}

fun exportMovementsCsv(context: Context, repo: Repository): File {
    val rows = repo.movements(1000).map { m ->
        listOf(
            m.onDate,
            m.productName,
            if (m.kind == "in") "Приход" else "Расход",
            m.qty.toString(),
            m.unit,
            m.note,
        )
    }
    return writeCsv(
        context,
        "coffemod-dvizheniya-${LocalDate.now()}.csv",
        listOf("Дата", "Товар", "Тип", "Количество", "Ед. изм.", "Комментарий"),
        rows,
    )
}

fun shareFile(context: Context, file: File, mime: String, chooserTitle: String = "Отправить") {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "CoffeMod: база товаров")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

/** Книга Excel: листы «Товары», «Движения», «Инвентаризации». */
fun exportXlsx(context: Context, repo: Repository): File {
    val groups = repo.groups().associate { it.id to it.name }
    val products = repo.products()

    val productRows = mutableListOf<List<Any?>>()
    productRows += listOf("Название", "Группа", "Ед. изм.", "Остаток", "Мин. остаток", "Срок годности", "Штрихкоды")
    products.forEach { p ->
        productRows += listOf(
            p.name,
            groups[p.groupId] ?: "",
            p.unit,
            p.stock,
            p.minStock,
            p.expiresOn ?: "",
            repo.codesFor(p.id).joinToString(", "),
        )
    }

    val movementRows = mutableListOf<List<Any?>>()
    movementRows += listOf("Дата", "Товар", "Тип", "Количество", "Ед. изм.", "Комментарий")
    repo.movements(1000).forEach { m ->
        movementRows += listOf(
            m.onDate,
            m.productName,
            if (m.kind == "in") "Приход" else "Расход",
            m.qty,
            m.unit,
            m.note,
        )
    }

    val sessionRows = mutableListOf<List<Any?>>()
    sessionRows += listOf("Дата", "Позиций", "Расхождений")
    repo.sessionsDone(100).forEach { s ->
        val items = repo.sessionItems(s.id)
        sessionRows += listOf(
            s.startedAt.take(16).replace('T', ' '),
            items.size,
            items.count { it.actual != it.expected },
        )
    }

    val file = File(context.cacheDir, "exports/coffemod-baza-${LocalDate.now()}.xlsx")
    Xlsx.write(
        file,
        listOf(
            Xlsx.Sheet("Товары", productRows),
            Xlsx.Sheet("Движения", movementRows),
            Xlsx.Sheet("Инвентаризации", sessionRows),
        ),
    )
    return file
}
