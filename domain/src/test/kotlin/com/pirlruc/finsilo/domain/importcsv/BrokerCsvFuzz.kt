package com.pirlruc.finsilo.domain.importcsv

/** Jazzer entry for broker CSV text. Parse errors are the expected outcome. */
class BrokerCsvFuzz {
    companion object {
        @JvmStatic
        fun fuzzerTestOneInput(data: ByteArray) {
            runCatching { BrokerCsv.parse(data.toString(Charsets.UTF_8)) }
        }
    }
}
