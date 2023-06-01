package net.corda.processors.messagepattern.internal.processors

import net.corda.messaging.api.records.Record
import net.corda.processors.messagepattern.internal.RecordSizeType
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom


val CHARS_PER_KB = 1000

fun generateOutputRecords(outputRecordCount: Int, outputTopic: String, key: String? = null, size: RecordSizeType = RecordSizeType.SMALL): List<Record<*, *>> {
    val outputRecords = mutableListOf<Record<*, *>>()
    repeat(outputRecordCount) {
        val recordKey = key ?: UUID.randomUUID().toString()
        outputRecords.add(generateOutputRecord(recordKey, outputTopic, size))
    }
    return outputRecords
}

fun generateOutputRecord(recordKey: String, outputTopic: String, size: RecordSizeType): Record<*, *> {
    return Record(outputTopic, recordKey, generateValue(size))
}

fun generateValue(size: RecordSizeType): String {
    return when(size) {
        RecordSizeType.SMALL ->  getLargeString(1)
        RecordSizeType.MEDIUM -> getLargeString(500)
        RecordSizeType.CHUNKED -> getLargeString(2000)
        RecordSizeType.CHECKPOINT -> getLargeString(64)
    }
}

fun getLargeString(kiloBytes: Int) : String {
    val stringBuilder = StringBuilder()
    for (i in 0..CHARS_PER_KB*kiloBytes) {
        stringBuilder.append(ThreadLocalRandom.current().nextInt(0,9) )
    }
    return stringBuilder.toString()
}