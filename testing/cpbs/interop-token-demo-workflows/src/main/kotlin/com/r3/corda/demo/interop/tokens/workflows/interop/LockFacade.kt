package com.r3.corda.demo.interop.tokens.workflows.interop

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.interop.binding.BindsFacade
import net.corda.v5.application.interop.binding.BindsFacadeMethod
import net.corda.v5.application.interop.binding.BindsFacadeParameter
import net.corda.v5.application.interop.binding.FacadeVersions
import net.corda.v5.base.annotations.Suspendable
import java.nio.ByteBuffer

@BindsFacade("org.corda.interop/platform/lock")
@FacadeVersions("v1.0")
interface LockFacade {

    @FacadeVersions("v1.0")
    @BindsFacadeMethod("create-lock")
    @Suspendable
    fun createLock(assetId: String,
                   recipient: String,
                   @BindsFacadeParameter("notary-keys") notaryKeys: ByteBuffer,
                   @BindsFacadeParameter("draft") draft: String): String

    @FacadeVersions("v1.0")
    @BindsFacadeMethod("unlock")
    @Suspendable
    fun unlock(
        reservationRef: String,
        @BindsFacadeParameter("signed-tx") proof: DigitalSignatureAndMetadata
    ): String

    @FacadeVersions("v1.0")
    @BindsFacadeMethod("send-proof")
    @Suspendable
    fun sendProof(
        @BindsFacadeParameter("signable-data") signableData: String,
        @BindsFacadeParameter("signed-tx") proof: DigitalSignatureAndMetadata,
        key: ByteBuffer
    ): String

}