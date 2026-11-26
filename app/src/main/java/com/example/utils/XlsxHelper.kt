package com.example.utils

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class XlsxSheet(
    val name: String,
    val rows: List<List<Any?>>
)

object XlsxHelper {

    fun buildXlsxBytes(sheets: List<XlsxSheet>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            val sheetOverrides = sheets.indices.joinToString("") { idx ->
                """<Override PartName="/xl/worksheets/sheet${idx + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
            }
            val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  $sheetOverrides
</Types>"""
            zos.write(contentTypes.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("_rels/.rels"))
            val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
            zos.write(rootRels.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            val wbRelsEntries = sheets.indices.joinToString("") { idx ->
                """<Relationship Id="rId${idx + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${idx + 1}.xml"/>"""
            }
            val styleRelId = "rId${sheets.size + 1}"
            val wbRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  $wbRelsEntries
  <Relationship Id="$styleRelId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
            zos.write(wbRels.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("xl/styles.xml"))
            val stylesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF163628"/><bgColor indexed="64"/></patternFill></fill>
  </fills>
  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="2">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
  </cellXfs>
</styleSheet>"""
            zos.write(stylesXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("xl/workbook.xml"))
            val sheetNodes = sheets.mapIndexed { idx, s ->
                val cleanName = escapeXml(s.name.take(31))
                """<sheet name="$cleanName" sheetId="${idx + 1}" r:id="rId${idx + 1}"/>"""
            }.joinToString("")
            val workbookXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>$sheetNodes</sheets>
</workbook>"""
            zos.write(workbookXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            sheets.forEachIndexed { idx, sheet ->
                zos.putNextEntry(ZipEntry("xl/worksheets/sheet${idx + 1}.xml"))
                val sb = StringBuilder()
                sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
                sheet.rows.forEachIndexed { rIdx, row ->
                    val rowNum = rIdx + 1
                    sb.append("""<row r="$rowNum">""")
                    row.forEachIndexed { cIdx, cellVal ->
                        val ref = "${colName(cIdx)}$rowNum"
                        val styleAttr = if (rIdx == 0) """ s="1"""" else ""
                        when (cellVal) {
                            is Number -> {
                                sb.append("""<c r="$ref"$styleAttr><v>${cellVal}</v></c>""")
                            }
                            else -> {
                                val txt = escapeXml(cellVal?.toString() ?: "")
                                sb.append("""<c r="$ref" t="inlineStr"$styleAttr><is><t>$txt</t></is></c>""")
                            }
                        }
                    }
                    sb.append("</row>")
                }
                sb.append("</sheetData></worksheet>")
                zos.write(sb.toString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    fun parseFirstSheetRows(inputStream: InputStream): Result<List<List<String>>> {
        return try {
            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        entries[entry.name] = zis.readBytes()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
            val sheetBytes = entries["xl/worksheets/sheet1.xml"]
                ?: entries.entries.firstOrNull { it.key.startsWith("xl/worksheets/sheet") && it.key.endsWith(".xml") }?.value
                ?: return Result.failure(IllegalArgumentException("File .xlsx tidak memiliki worksheet yang valid."))
            val rows = parseSheetXml(sheetBytes, sharedStrings)
            Result.success(rows)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Gagal membaca file .xlsx: ${e.message}"))
        }
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")
        var inSi = false
        var inT = false
        val sb = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "si") {
                        inSi = true
                        sb.clear()
                    } else if (inSi && parser.name == "t") {
                        inT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inSi && inT) sb.append(parser.text ?: "")
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "t") inT = false
                    else if (parser.name == "si") {
                        inSi = false
                        result.add(sb.toString())
                    }
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun parseSheetXml(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = Xml.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")
        var currentRow = mutableMapOf<Int, String>()
        var currentCol = 0
        var cellType = ""
        var inV = false
        var inT = false
        val textBuf = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> currentRow = mutableMapOf()
                        "c" -> {
                            val ref = parser.getAttributeValue(null, "r")
                            currentCol = if (ref != null) colIndexFromRef(ref) else currentRow.size
                            cellType = parser.getAttributeValue(null, "t") ?: ""
                            textBuf.clear()
                        }
                        "v" -> {
                            inV = true
                            textBuf.clear()
                        }
                        "t" -> {
                            inT = true
                            textBuf.clear()
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inV || inT) textBuf.append(parser.text ?: "")
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "v" -> {
                            inV = false
                            val raw = textBuf.toString()
                            val value = if (cellType == "s") {
                                raw.toIntOrNull()?.let { idx -> sharedStrings.getOrNull(idx) ?: "" } ?: ""
                            } else raw
                            currentRow[currentCol] = value
                        }
                        "t" -> {
                            inT = false
                            if (cellType == "inlineStr" || cellType == "str") {
                                currentRow[currentCol] = textBuf.toString()
                            }
                        }
                        "row" -> {
                            val maxCol = (currentRow.keys.maxOrNull() ?: -1)
                            if (maxCol >= 0) {
                                val list = (0..maxCol).map { currentRow[it] ?: "" }
                                if (list.any { it.isNotBlank() }) rows.add(list)
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun colIndexFromRef(ref: String): Int {
        var idx = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') {
                idx = idx * 26 + (ch - 'A' + 1)
            } else if (ch in 'a'..'z') {
                idx = idx * 26 + (ch - 'a' + 1)
            } else break
        }
        return (idx - 1).coerceAtLeast(0)
    }

    private fun colName(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (i >= 0) {
            sb.insert(0, ('A' + (i % 26)))
            i = (i / 26) - 1
        }
        return sb.toString()
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
