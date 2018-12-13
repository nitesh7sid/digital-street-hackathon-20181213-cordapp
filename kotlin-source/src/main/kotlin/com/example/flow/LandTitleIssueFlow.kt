package com.example.flow


import co.paralleluniverse.fibers.Suspendable
import com.example.contract.LandTitleContract
import com.example.state.LandTitleState
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.seconds
import java.time.Instant


/**
 * Flow for the Bank of Corda node to issue some commercial paper to the seller's node, to sell to the buyer.
 */
@InitiatingFlow
@StartableByRPC
class LandTitleIssueFlow( val landTitleState: LandTitleState,
                          val recipient: Party) : FlowLogic<SignedTransaction>() {
    companion object {

        object ISSUING : ProgressTracker.Step("Issuing and timestamping some commercial paper")

        fun tracker() = ProgressTracker(
                ISSUING)
    }
    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = ISSUING

            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            val issuance: SignedTransaction = run {
            val tx = TransactionBuilder(notary = notary)

            tx.addOutputState(landTitleState, LandTitleContract.LAND_TITLE_CONTRACT_ID)

            // Requesting a time-window to be set, all CP must have a validation window.
            tx.setTimeWindow(Instant.now(), 30.seconds)

            // Sign it as ourselves.
            val stx = serviceHub.signInitialTransaction(tx)

            subFlow(FinalityFlow(stx))
        }

        // Now make a dummy transaction that moves it to a new key, just to show that resolving dependencies works.
        val move: SignedTransaction = run {
            val builder = TransactionBuilder(notary)
            LandTitleContract().generateMove(builder, issuance.tx.outRef(0), recipient)
            val stx = serviceHub.signInitialTransaction(builder)
            subFlow(FinalityFlow(stx))
        }

        return move
    }
}