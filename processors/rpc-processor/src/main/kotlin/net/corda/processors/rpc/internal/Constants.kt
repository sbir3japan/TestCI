package net.corda.processors.rpc.internal

internal val CONFIG_HTTP_RPC =
    """address="0.0.0.0:8888"
        context.description="Exposing RPCOps interfaces as OpenAPI WebServices"
        context.title="HTTP RPC"""".trimIndent()
internal const val CONFIG_CONFIG_MGMT_REQUEST_TIMEOUT = "timeout.millis=\"10000\""

internal const val CLIENT_ID_RPC_PROCESSOR = "rpc.processor"