package com.rajmani7584.payloaddumper.nativeHelper

import com.rajmani7584.payloaddumper.model.PayloadType

object PayloadDumper {

    private external fun initSession(pType: Int, path: String): ByteArray
    private external fun fetchHeader(): String

    init {
        System.loadLibrary("payload_dumper")
    }

    fun init(payloadType: PayloadType): ByteArray {
        return initSession(payloadType.getTypeInt(), payloadType.getPathString())
    }

    fun getHeader(): String {
        return fetchHeader()
    }
}