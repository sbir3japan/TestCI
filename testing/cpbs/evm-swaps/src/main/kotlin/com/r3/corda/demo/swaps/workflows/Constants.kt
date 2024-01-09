package com.r3.corda.demo.swaps.workflows

class Constants {
    companion object {
        val RPC_URL = System.getenv("DEMO_RPC_URL") ?: "http://host.docker.internal:8545"
    }
}