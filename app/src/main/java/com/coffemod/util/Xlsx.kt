package com.coffemod.util

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Минимальный генератор XLSX без внешних библиотек:
 * один или несколько листов, текстовые и числовые ячейки.
 */
object Xlsx {

    data class Sheet(val name: String, val rows: List<List<Any?>>)

    fun write(file: File, sheets: List<Sheet>) {
        file.parentFile?.mkdirs()
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            put(zip, "[Content_Types].xml", contentTypes(sheets.size))
            put(zip, "_rels/.rels", rootRels())
            put(zip, "xl/workbook.xml", workbook(sheets))
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            sheets.forEachIndexed { index, sheet ->
                put(zip, "xl/worksheets/sheet${index + 1}.xml", sheetXml(sheet))
            }
        }
    }

    private fun put(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("\n", "&#10;")

    private fun column(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (i >= 0) {
            sb.insert(0, ('A' + i % 26))
            i = i / 26 - 1
        }
        return sb.toString()
    }

    private fun contentTypes(count: Int): String {
        val overrides = (1..count).joinToString("") {
            "<Override PartName=\"/xl/worksheets/sheet$it.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
$overrides
</Types>"""
    }

    private fun rootRels(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbook(sheets: List<Sheet>): String {
        val entries = sheets.mapIndexed { index, sheet ->
            "<sheet name=\"${escape(sheet.name)}\" sheetId=\"${index + 1}\" r:id=\"rId${index + 1}\"/>"
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets>$entries</sheets>
</workbook>"""
    }

    private fun workbookRels(count: Int): String {
        val rels = (1..count).joinToString("") {
            "<Relationship Id=\"rId$it\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet$it.xml\"/>"
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
$rels
</Relationships>"""
    }

    private fun sheetXml(sheet: Sheet): String {
        val rows = StringBuilder()
        sheet.rows.forEachIndexed { rowIndex, cells ->
            rows.append("<row r=\"${rowIndex + 1}\">")
            cells.forEachIndexed { colIndex, value ->
                val ref = "${column(colIndex)}${rowIndex + 1}"
                when (value) {
                    null -> {}
                    is Number -> rows.append("<c r=\"$ref\"><v>${value}</v></c>")
                    else -> rows.append(
                        "<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${escape(value.toString())}</t></is></c>"
                    )
                }
            }
            rows.append("</row>")
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<sheetData>$rows</sheetData>
</worksheet>"""
    }
}
