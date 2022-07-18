package net.corda.membership.httprpc.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.membership.httprpc.v1.types.response.HsmInfo

@HttpRpcResource(
    name = "HsmRpcOps",
    description = "HSM API",
    path = "hsm"
)
interface HsmRpcOps : RpcOps {
    /**
     * GET endpoint which list the available HSMs.
     *
     * @return A list of available HSMs.
     */
    @HttpRpcGET(
        description = "Get list of available HSMs."
    )
    fun listHsms(): Collection<HsmInfo>

    /**
     * GET endpoint which list the assigned HSMs.
     *
     * @return A list of assigned HSMs.
     */
    @HttpRpcGET(
        path = "{tenantId}/{category}",
        description = "Get list of assigned HSMs."
    )
    fun assignedHsm(
        @HttpRpcPathParameter(description = "'p2p', 'rpc-api' or holding identity ID.")
        tenantId: String,
        @HttpRpcPathParameter(description = "Category of the HSM.  E.g. LEDGER, TLS, etc.")
        category: String
    ): HsmInfo?

    /**
     * POST endpoint which assign soft HSM.
     *
     * @return The newly assigned HSM.
     */
    @HttpRpcPOST(
        path = "soft/{tenantId}/{category}",
        description = "Assign soft HSM"
    )
    fun assignSoftHsm(
        @HttpRpcPathParameter(description = "'p2p', 'rpc-api' or holding identity ID.")
        tenantId: String,
        @HttpRpcPathParameter(description = "Category of the HSM.  E.g. LEDGER, TLS, etc.")
        category: String
    ): HsmInfo

    /**
     * POST endpoint which assign HSM.
     *
     * @return The newly assigned HSM.
     */
    @HttpRpcPOST(
        path = "{tenantId}/{category}",
        description = "Assign HSM."
    )
    fun assignHsm(
        @HttpRpcPathParameter(description = "'p2p', 'rpc-api' or holding identity ID.")
        tenantId: String,
        @HttpRpcPathParameter(description = "Category of the HSM.  E.g. LEDGER, TLS, etc.")
        category: String
    ): HsmInfo
}
