package com.r3.corda.demo.swaps.workflows

class Constants {
    companion object {
        // Default is to use the docker instance, change to local if running locally
        @Suppress("unused") private val DOCKER_INSTANCE_RPC_URL =  "http://host.docker.internal:8545"
        @Suppress("unused") private val LOCAL_INSTANCE_RPC_URL =  "http://localhost:8545"

        val RPC_URL = DOCKER_INSTANCE_RPC_URL
    }
}
