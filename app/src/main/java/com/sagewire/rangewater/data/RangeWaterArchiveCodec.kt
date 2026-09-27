package com.sagewire.rangewater.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Versioned, checksummed archive codec. It contains no Android storage assumptions. */
object RangeWaterArchiveCodec {
    const val FORMAT_ID = "com.sagewire.rangewater.backup"
    const val FORMAT_VERSION = 2
    const val DATABASE_SCHEMA_VERSION = 7
    const val MIME_TYPE = "application/vnd.sagewire.rangewater-backup"
    const val FILE_EXTENSION = ".rangewater"

    private const val MANIFEST_ENTRY = "manifest.json"
    private const val DATA_ENTRY = "data.json"
    private const val MAX_ARCHIVE_BYTES = 32 * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 24 * 1024 * 1024
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    fun write(
        output: OutputStream,
        data: RangeWaterBackupData,
        appVersionName: String,
        createdAt: Long = System.currentTimeMillis()
    ): RangeWaterBackupManifest {
        RangeWaterBackupValidator.validate(data)
        val dataBytes = gson.toJson(data).toByteArray(StandardCharsets.UTF_8)
        require(dataBytes.size <= MAX_ENTRY_BYTES) { "RangeWater backup is too large" }
        val manifest = RangeWaterBackupManifest(
            formatId = FORMAT_ID,
            formatVersion = FORMAT_VERSION,
            databaseSchemaVersion = DATABASE_SCHEMA_VERSION,
            appVersionName = appVersionName,
            createdAt = createdAt,
            dataSha256 = sha256(dataBytes),
            recordCounts = data.recordCounts()
        )
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(gson.toJson(manifest).toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(DATA_ENTRY))
            zip.write(dataBytes)
            zip.closeEntry()
        }
        return manifest
    }

    fun toByteArray(
        data: RangeWaterBackupData,
        appVersionName: String,
        createdAt: Long = System.currentTimeMillis()
    ): ByteArray = ByteArrayOutputStream().use { output ->
        write(output, data, appVersionName, createdAt)
        output.toByteArray()
    }

    fun read(input: InputStream): PreparedRangeWaterRestore {
        val limitedBytes = input.readAtMost(MAX_ARCHIVE_BYTES)
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(limitedBytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory) { "Backup contains an unexpected directory" }
                require(entry.name == MANIFEST_ENTRY || entry.name == DATA_ENTRY) {
                    "Backup contains an unexpected entry: ${entry.name}"
                }
                require(entry.name !in entries) { "Backup contains duplicate entries" }
                entries[entry.name] = zip.readAtMost(MAX_ENTRY_BYTES)
                zip.closeEntry()
            }
        }
        val manifestBytes = entries[MANIFEST_ENTRY] ?: throw IllegalArgumentException("Backup manifest is missing")
        val dataBytes = entries[DATA_ENTRY] ?: throw IllegalArgumentException("Backup data is missing")
        require(entries.size == 2) { "Backup structure is invalid" }

        val manifest = try {
            gson.fromJson(String(manifestBytes, StandardCharsets.UTF_8), RangeWaterBackupManifest::class.java)
        } catch (error: Exception) {
            throw IllegalArgumentException("Backup manifest cannot be read", error)
        } ?: throw IllegalArgumentException("Backup manifest is empty")

        require(manifest.formatId == FORMAT_ID) { "This is not a RangeWater backup" }
        require(manifest.formatVersion in 1..FORMAT_VERSION) {
            if (manifest.formatVersion > FORMAT_VERSION) {
                "This backup was created by a newer RangeWater backup format"
            } else {
                "This backup format is no longer supported"
            }
        }
        require(manifest.databaseSchemaVersion <= DATABASE_SCHEMA_VERSION) {
            "This backup requires a newer RangeWater database version"
        }
        require(manifest.dataSha256.equals(sha256(dataBytes), ignoreCase = true)) {
            "Backup integrity check failed"
        }

        val data = try {
            val json = JsonParser.parseString(String(dataBytes, StandardCharsets.UTF_8)).asJsonObject
            if (manifest.formatVersion == 1) {
                LEGACY_EMPTY_COLLECTIONS.forEach { name ->
                    if (!json.has(name) || json[name].isJsonNull) json.add(name, com.google.gson.JsonArray())
                }
            }
            gson.fromJson(json, RangeWaterBackupData::class.java)
        } catch (error: Exception) {
            throw IllegalArgumentException("Backup records cannot be read", error)
        } ?: throw IllegalArgumentException("Backup records are empty")

        RangeWaterBackupValidator.validate(data)
        require(manifest.recordCounts == data.recordCounts()) { "Backup record counts do not match" }
        return PreparedRangeWaterRestore(manifest, data)
    }

    private fun InputStream.readAtMost(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { "Backup exceeds the supported size" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { String.format(Locale.US, "%02x", it.toInt() and 0xff) }

    private val LEGACY_EMPTY_COLLECTIONS = listOf(
        "grazingCircuits",
        "circuitPastures",
        "circuitPastureRoles",
        "herdCircuitAssignments",
        "pastureForageObservations"
    )
}
