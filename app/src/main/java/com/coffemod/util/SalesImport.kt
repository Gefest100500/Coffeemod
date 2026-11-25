package com.coffemod.util

import com.coffemod.data.Product
import com.coffemod.data.Repository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class SaleLine(
    val date: String,
    val raw: String,
    val qty: Double,
    val product: Product?,
)

/**
 * Импорт продаж из CSV/TSV: строка = «дата; товар или код; количество».
 * Разделитель определяется автоматически (; , табуляция), заголовок пропускается.
 */
object SalesImport {

    private val datePatterns = listOf("yyyy-MM-dd", "dd.MM.yyyy", "dd.MM.yy", "yyyy/MM/dd", "dd/MM/yyyy")

    fun normalizeDate(raw: String): String {
        val value = raw.trim()
        for (pattern in datePatterns) {
            runCatching {
                return LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern)).toString()
            }
        }
        return LocalDate.now().toString()
    }

    fun parse(text: String, repo: Repository): List<SaleLine> {
        val lines = text
            .removePrefix("\uFEFF")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
        if (lines.isEmpty()) return emptyList()

        val delimiter = listOf(';', ',', '\t').maxByOrNull { candidate ->
            lines.take(3).sumOf { line -> line.count { it == candidate } }
        } ?: ';'

        val byName = repo.products().associateBy { it.name.trim().lowercase() }

        var start = 0
        val first = lines.first().lowercase()
        if (first.contains("дата") || first.contains("date") || first.contains("товар") ||
            first.contains("наимен") || first.contains("код") || first.contains("name")
        ) {
            start = 1
        }

        val result = mutableListOf<SaleLine>()
        for (index in start until lines.size) {
            val parts = lines[index].split(delimiter)
            if (parts.size < 3) continue
            val date = parts[0].trim()
            val raw = parts[1].trim()
            val qty = parts[2].trim().replace(',', '.').toDoubleOrNull() ?: continue
            if (qty <= 0 || raw.isEmpty()) continue
            val product = repo.productByCode(raw) ?: byName[raw.lowercase()]
            result += SaleLine(normalizeDate(date), raw, qty, product)
        }
        return result
    }

    fun import(rows: List<SaleLine>, repo: Repository, note: String): Int {
        var count = 0
        rows.forEach { row ->
            val product = row.product ?: return@forEach
            repo.addMovement(product.id, "out", row.qty, note, row.date)
            count++
        }
        return count
    }
}
