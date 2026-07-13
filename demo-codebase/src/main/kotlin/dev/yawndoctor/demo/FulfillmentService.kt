package dev.yawndoctor.demo

class FulfillmentService(
    private val transactor: YawnTestTransactor,
    private val shippingClient: ShippingClient,
    private val eventPublisher: EventPublisher,
) {

    /** Risky: external I/O inside a transaction via annotated function */
    @Transactional
    fun fulfillOrderAnnotated(orderId: Long) {
        shippingClient.reserve(orderId)
    }

    /** Risky: external I/O inside a transaction via lambda-based transactor */
    fun fulfillOrderLambda(orderId: Long) {
        transactor.open { session ->
            shippingClient.reserve(orderId)
            eventPublisher.publish("order fulfilled")
        }
    }

    /** Safe: external I/O outside any transaction scope */
    fun notifyCustomer(orderId: Long) {
        shippingClient.notify(orderId)
    }
}
