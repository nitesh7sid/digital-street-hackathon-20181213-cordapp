package com.example.flow


import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.finance.contracts.CommercialPaper
import net.corda.finance.contracts.getCashBalances
import net.corda.finance.flows.TwoPartyTradeFlow
import java.util.*

@InitiatedBy(SellerFlow::class)
class BuyerFlow(private val otherSideSession: FlowSession) : FlowLogic<Unit>() {

    object STARTING_BUY : ProgressTracker.Step("Seller connected, purchasing commercial paper asset")

    override val progressTracker: ProgressTracker = ProgressTracker(STARTING_BUY)

    @Suspendable
    override fun call() {
        progressTracker.currentStep = STARTING_BUY

        // Receive the offered amount and automatically agree to it (in reality this would be a longer negotiation)
        val amount = otherSideSession.receive<Amount<Currency>>().unwrap { it }
        require(serviceHub.networkMapCache.notaryIdentities.isNotEmpty()) { "No notary nodes registered" }
        val notary: Party = serviceHub.networkMapCache.notaryIdentities.first()
        val buyer = TwoPartyTradeFlow.Buyer(
                otherSideSession,
                notary,
                amount,
                CommercialPaper.State::class.java)

        // This invokes the trading flow and out pops our finished transaction.
        val tradeTX: SignedTransaction = subFlow(buyer)

        logBalance()
    }

    private fun logBalance() {
        val balances = serviceHub.getCashBalances().entries.map { "${it.key.currencyCode} ${it.value}" }
        println("Remaining balance: ${balances.joinToString()}")
    }

}