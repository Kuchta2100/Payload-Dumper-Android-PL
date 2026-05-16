package com.rajmani7584.payloaddumper.model

import com.rajmani7584.payloaddumper.engine.chromeos_update_engine.UpdateMetadata

data class Payload(val name: String, val type: PayloadType, val manifest: UpdateMetadata.DeltaArchiveManifest)

sealed class PayloadType {
    data class LocalPayload(val path: String): PayloadType()
    data class RemotePayload(val path: String): PayloadType()

    fun getTypeInt(): Int {
        return when (this) {
            is LocalPayload -> 0
            is RemotePayload -> 1
        }
    }
    fun getPathString(): String {
        return when (this) {
            is LocalPayload -> this.path
            is RemotePayload -> this.path
        }
    }
}