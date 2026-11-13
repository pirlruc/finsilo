package com.pirlruc.finsilo.domain.vault

/**
 * One ledger on the device. The default row always uses the original database file name.
 */
data class VaultRecord(val id: String, val fileName: String, val label: String)

/**
 * Text registry of vaults. A missing file means the original database is the only vault.
 * Decoding never renames that file.
 */
object VaultRegistry {
    const val DEFAULT_ID: String = "default"
    const val LEGACY_FILE: String = "finsilo.db"
    const val REGISTRY_FILE: String = "finsilo-vaults.txt"

    /** The single-vault layout used before a registry file exists. */
    fun missing(): List<VaultRecord> = listOf(VaultRecord(DEFAULT_ID, LEGACY_FILE, "Personal"))

    /** Database file for [id]. The default id is always [LEGACY_FILE]. */
    fun fileName(id: String): String = if (id == DEFAULT_ID) LEGACY_FILE else "finsilo-$id.db"

    /** Parse [text]. Blank input is the implicit default vault. */
    fun decode(text: String): List<VaultRecord> {
        if (text.isBlank()) return missing()
        val parsed = text.lineSequence().map { it.trim() }.filter { keep(it) }.mapNotNull { parse(it) }.toList()
        if (parsed.isEmpty()) return missing()
        val rows = if (parsed.any { it.id == DEFAULT_ID }) parsed else missing() + parsed
        return rows.map { pinFile(it) }
    }

    /** One registry record per line. */
    fun encode(records: List<VaultRecord>): String = records.joinToString("\n") { row ->
        "${row.id}|${fileName(row.id)}|${row.label.replace('|', ' ')}"
    } + "\n"

    /** Append a vault. Returns null when the label cannot become a new id. */
    fun add(existing: List<VaultRecord>, label: String): VaultRecord? {
        val id = label.lowercase().filter { it.isLetterOrDigit() }.take(MAX_ID)
        if (id.isEmpty() || id == DEFAULT_ID || existing.any { it.id == id }) return null
        return VaultRecord(id, fileName(id), label.trim().ifEmpty { id })
    }

    /** True when [id] is already in [records]. */
    fun contains(records: List<VaultRecord>, id: String): Boolean = records.any { it.id == id }

    private fun keep(line: String): Boolean = line.isNotEmpty() && !line.startsWith("#")

    private fun parse(line: String): VaultRecord? {
        val parts = line.split('|', limit = 3)
        if (parts.size < 3) return null
        val id = parts[0].trim()
        if (id.isEmpty() || !id.all { it.isLetterOrDigit() }) return null
        return VaultRecord(id, parts[1].trim(), parts[2].trim().ifEmpty { id })
    }

    private fun pinFile(record: VaultRecord): VaultRecord = record.copy(fileName = fileName(record.id))

    private const val MAX_ID: Int = 16
}

/** Preference key for one vault. The default vault keeps the original key names. */
object VaultKeys {
    /** Unprefixed for the default vault so an existing install still unlocks. */
    fun of(vaultId: String, name: String): String = if (vaultId == VaultRegistry.DEFAULT_ID) name else "vault.$vaultId.$name"
}

/** Mutable id shared by the lock and the database key store. */
class VaultSelection(var id: String = VaultRegistry.DEFAULT_ID)
