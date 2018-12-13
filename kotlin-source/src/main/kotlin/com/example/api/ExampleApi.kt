package com.example.api


import com.example.schema.IOUSchemaV1
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import com.example.flow.LandTitleIssueFlow
import com.example.state.LandTitleState
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import org.slf4j.Logger
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status.BAD_REQUEST
import javax.ws.rs.core.Response.Status.CREATED
import com.example.flow.SellerFlow

val SERVICE_NAMES = listOf("Notary", "Network Map Service")

// This API is accessible from /api/example. All paths specified below are relative to it.
@Path("example")
class ExampleApi(private val rpcOps: CordaRPCOps) {
    private val myLegalName: CordaX500Name = rpcOps.nodeInfo().legalIdentities.first().name

    companion object {
        private val logger: Logger = loggerFor<ExampleApi>()
    }

    /**
     * Returns the node's name.
     */
    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    fun whoami() = mapOf("me" to myLegalName)

    /**
     * Returns all parties registered with the [NetworkMapService]. These names can be used to look up identities
     * using the [IdentityService].
     */
    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPeers(): Map<String, List<CordaX500Name>> {
        val nodeInfo = rpcOps.networkMapSnapshot()
        return mapOf("peers" to nodeInfo
                .map { it.legalIdentities.first().name }
                //filter out myself, notary and eventual network map started by driver
                .filter { it.organisation !in (SERVICE_NAMES + myLegalName.organisation) })
    }

    /**
     * Displays all IOU states that exist in the node's vault.
     */
    @GET
    @Path("land-titles")
    @Produces(MediaType.APPLICATION_JSON)
    fun getLandTitles() = rpcOps.vaultQueryBy<LandTitleState>().states

    /**
     * Initiates a flow to agree an IOU between two parties.
     *
     * Once the flow finishes it will have written the IOU to ledger. Both the lender and the borrower will be able to
     * see it when calling /api/example/ious on their respective nodes.
     *
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */
    @PUT
    @Path("issue-cash")
    fun issueCash(@QueryParam("amount") amount: Int, @QueryParam("buyer") buyer: CordaX500Name): Response {
        val amountData = amount.DOLLARS
        val notaryIdentity = rpcOps.notaryIdentities()[0]
        val buyerParty = rpcOps.wellKnownPartyFromX500Name(buyer)
                ?: return Response.status(BAD_REQUEST).entity("Party named $buyer cannot be found.\n").build()

        return try {

            rpcOps.startFlow(::CashIssueFlow, amountData, OpaqueBytes.of(1), notaryIdentity).returnValue.getOrThrow()

            // TODO This can't be done in parallel, perhaps due to soft-locking issues?
            val signedTx = rpcOps.startFlow(::CashPaymentFlow, amountData, buyerParty).returnValue.getOrThrow()

            println("Cash issued to buyer")
            Response.status(CREATED).entity("Transaction id ${signedTx.stx.tx.id} committed to ledger.\n").build()
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(BAD_REQUEST).entity(ex.message!!).build()
        }
    }

    @PUT
    @Path("issue-land")
    fun issueLandTitle(@QueryParam("titleID") titleID: String, @QueryParam("owner") ownernName: CordaX500Name, @QueryParam("issuer") issuerName: CordaX500Name): Response {

        val otherParty = rpcOps.wellKnownPartyFromX500Name(ownernName)
                ?: return Response.status(BAD_REQUEST).entity("Party named $ownernName cannot be found.\n").build()

        val issuerParty = rpcOps.wellKnownPartyFromX500Name(issuerName)
                ?: return Response.status(BAD_REQUEST).entity("Party named $issuerName cannot be found.\n").build()

        val landTitleState = LandTitleState(titleID, issuerParty, otherParty)
        return try {
            val signedTx = rpcOps.startFlow(::LandTitleIssueFlow, landTitleState, otherParty).returnValue.getOrThrow()
            Response.status(CREATED).entity("Transaction id ${signedTx.id} committed to ledger.\n").build()

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(BAD_REQUEST).entity(ex.message!!).build()
        }
    }

    @PUT
    @Path("transfer-land")
    fun transferLandTitle(@QueryParam("amount") amount: Int, @QueryParam("owner") buyer: CordaX500Name): Response {
        val amountData = amount.DOLLARS
        val otherParty = rpcOps.wellKnownPartyFromX500Name(buyer) ?:
        return Response.status(BAD_REQUEST).entity("Party named $buyer cannot be found.\n").build()

        return try {
            val signedTx = rpcOps.startFlow(::SellerFlow, otherParty, amountData).returnValue.getOrThrow()
            Response.status(CREATED).entity("Transaction id ${signedTx.id} committed to ledger.\n").build()

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(BAD_REQUEST).entity(ex.message!!).build()
        }
   }
}