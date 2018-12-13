package com.example.contract

import com.example.state.LandTitleState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class LandTitleContract : Contract {
    companion object {
        @JvmStatic
        val LAND_TITLE_CONTRACT_ID = "com.example.contract.LandTitleContract"
    }

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    override fun verify(tx: LedgerTransaction) {
        val commands = tx.commands.requireSingleCommand<Commands>()
        val setOfSigners = commands.signers.toSet()
        when (commands.value){
            is Commands.IssueLandTitle -> verifyIssueLandTitle(tx, setOfSigners)
            is Commands.Move -> verifyMoveLandTitle(tx, setOfSigners)

            else -> throw IllegalArgumentException("Unrecognised Command")
        }
    }

    private fun verifyIssueLandTitle(tx: LedgerTransaction, setOfSigners: Set<PublicKey>) = requireThat {

        val output = tx.outputsOfType<LandTitleState>().single()
        val time = tx.timeWindow?.untilTime ?: throw IllegalArgumentException("Issuances have a time-window")
        requireThat {
            // Don't allow people to issue commercial paper under other entities identities.
            "output states are issued by a command signer" using
                    (output.titleIssuer.owningKey in setOfSigners)
            "No input during the issuances" using tx.inputs.isEmpty()
        }
    }

    private fun verifyMoveLandTitle(tx: LedgerTransaction, setOfSigners: Set<PublicKey>) = requireThat {
        val output = tx.outputsOfType<LandTitleState>().single()
        "the transaction is signed by the owner of the land title" using (output.owner.owningKey in setOfSigners)
        "the state is propagated" using (tx.outputs.size == 1)
    }

    /**
     * This contract only implements one command, Create.
     */
    interface Commands : CommandData {
        class IssueLandTitle : TypeOnlyCommandData(), Commands
        class Move : TypeOnlyCommandData(), Commands
    }
}
