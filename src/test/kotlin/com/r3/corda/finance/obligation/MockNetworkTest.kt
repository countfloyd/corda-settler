package com.r3.corda.finance.obligation

import com.r3.corda.finance.obligation.client.flows.CreateObligation
import com.r3.corda.finance.obligation.client.flows.OffLedgerSettleObligation
import com.r3.corda.finance.obligation.client.flows.UpdateSettlementMethod
import com.r3.corda.finance.obligation.states.Obligation
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.toFuture
import net.corda.core.transactions.SignedTransaction
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before

abstract class MockNetworkTest(val numberOfNodes: Int) {

    protected val network = MockNetwork(
            cordappPackages = listOf(
                    "com.r3.corda.finance.obligation",
                    "com.r3.corda.finance.obligation.client",
                    "com.r3.corda.finance.ripple",
                    "com.r3.corda.finance.obligation.oracle",
                    "com.r3.corda.finance.obligation"
            ),
            threadPerNode = true
    )

    /** The nodes which makes up the network. */
    protected lateinit var nodes: List<StartedMockNode>

    /** Override this to assign each node to a variable for ease of use. */
    @Before
    abstract fun initialiseNodes()

    @Before
    fun setupNetwork() {
        nodes = createSomeNodes(numberOfNodes)
    }

    @After
    fun tearDownNetwork() {
        network.stopNodes()
    }

    private fun createSomeNodes(numberOfNodes: Int = 2): List<StartedMockNode> {
        val partyNodes = (1..numberOfNodes).map { current ->
            val char = current.toChar() + 64
            val name = CordaX500Name("Party$char", "London", "GB")
            network.createPartyNode(name)
        }
        return partyNodes
    }

    /** Create a new obligation with the supplied parameters. */
    fun <T : Money> StartedMockNode.createObligation(
            faceAmount: Amount<T>,
            counterparty: StartedMockNode,
            role: CreateObligation.InitiatorRole
    ): CordaFuture<SignedTransaction> {
        return transaction {
            val flow = CreateObligation.Initiator(faceAmount, role, counterparty.legalIdentity())
            startFlow(flow)
        }
    }

    /** Add settlement instructions to existing obligation. */
    fun StartedMockNode.addSettlementInstructions(linearId: UniqueIdentifier, settlementMethod: SettlementMethod): CordaFuture<SignedTransaction> {
        return transaction {
            val flow = UpdateSettlementMethod(linearId, settlementMethod)
            startFlow(flow)
        }
    }

    /** Add settlement instructions to existing obligation. */
    fun StartedMockNode.makePayment(linearId: UniqueIdentifier): CordaFuture<SignedTransaction> {
        return transaction { startFlow(OffLedgerSettleObligation(linearId)) }
    }

    fun StartedMockNode.legalIdentity() = services.myInfo.legalIdentities.first()

    /** From a transaction which produces a single output, retrieve that output. */
    inline fun <reified T : LinearState> SignedTransaction.singleOutput() = tx.outRefsOfType<T>().single()

    inline fun <reified T : LinearState> StateAndRef<T>.linearId() = state.data.linearId

    fun StartedMockNode.queryObligationById(linearId: UniqueIdentifier): StateAndRef<Obligation<DigitalCurrency>> {
        return transaction {
            val query = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
            services.vaultService.queryBy<Obligation<DigitalCurrency>>(query).states.single()
        }
    }

    /** Check to see if a node recorded a transaction with a particular hash. Return a future signed transaction. */
    fun StartedMockNode.watchForTransaction(txId: SecureHash): CordaFuture<SignedTransaction> {
        return transaction { services.validatedTransactions.updates.filter { it.id == txId }.toFuture() }
    }

}