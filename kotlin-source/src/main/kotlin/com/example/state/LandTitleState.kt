package com.example.state


import com.example.contract.LandTitleContract
import net.corda.core.contracts.CommandAndState
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.OwnableState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party


/**
 * The state object recording IOU agreements between two parties.
 *
 * A state must implement [ContractState] or one of its descendants.
 *
 * @param value the value of the IOU.
 * @param lender the party issuing the IOU.
 * @param borrower the party receiving and approving the IOU.
 */
data class LandTitleState(val titleID: String,
                          val titleIssuer: Party, override val owner: AbstractParty,
                          override val participants: List<AbstractParty> = listOf(owner)): OwnableState {

    override fun withNewOwner(newOwner: AbstractParty) = CommandAndState(LandTitleContract.Commands.Move(), copy(owner = newOwner))

     fun withOwner(newOwner: AbstractParty): LandTitleState = copy(owner = newOwner)

}
