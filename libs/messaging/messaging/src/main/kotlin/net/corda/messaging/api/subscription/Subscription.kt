package net.corda.messaging.api.subscription

import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.Resource

interface SubscriptionBase : Resource {
    /**
     * The name of the lifecycle coordinator inside the subscription. You can register a different coordinator to listen
     * for status changes from this subscription by calling [followStatusChangesByName] and passing in this value.
     */
    val subscriptionName: LifecycleCoordinatorName

    /**
     * Start a subscription.
     */
    fun start()
}
/**
 * A subscription that can be used to manage the life cycle of consumption of event records from a topic.
 * Records are key/value pairs represented by [K] and [V], respectively, and are analogous to a kafka record.
 *
 * See [SubscriptionFactory] for the creation of each subscription.
 *
 * Each subscription will have a different processor for sending feed updates to the user.  See
 * [SubscriptionFactory] and the processor docs themselves for more details on each type.
 *
 * A subscription will begin consuming events upon start().
 * A subscription will stop consuming events and close the connection upon close()/stop()
 */
interface Subscription<K, V> : SubscriptionBase {

    /**
     * Check the state of a subscription. true if subscription is still active. false otherwise.
     */
    val isRunning: Boolean
}

/**
 * A subscription that handles requests of type [REQUEST], processes the request and returns a response of type [RESPONSE].
 *
 * Requests are processed synchronously or asynchronously depending on the implementation. Requests are consumed as soon as they
 * have been posted to the request processor. Requests over HTTP/RPC may be unreliable or result in transient errors, this
 * should be considered and retry logic handled by the client.
 *
 * See [SubscriptionFactory] for the creation of each subscription.
 *
 * Each subscription will have a different processor for processing requests. See [SubscriptionFactory] for creation of
 * subscriptions. See [SyncRPCProcessor] and [RPCResponderProcessor] for more details on implementing a processor.
 *
 * A subscription will be available to accept requests after it has been started. A subscription will stop accepting requests
 * and close any connections on close or stop.
 */
interface RPCSubscription<REQUEST, RESPONSE> : SubscriptionBase

/**
 * A subscription that can be used to manage the life cycle of consumption of both state and event records from a
 * pair of topics.
 *
 * [StateAndEventSubscription]s actually process two feeds, one for states and one for events.  The state feed is
 * treated as, and probably is, a compacted topic.  The subscription will retain the most recent state from the feed.
 * The events are treated as a durable feed as to avoid missing any.  Each event may then trigger an update of the
 * corresponding state or, indeed, trigger more events.
 *
 * See [SubscriptionFactory] for the creation of this subscription.
 *
 * Feed updates will be returned via a [StateAndEventProcessor].
 *
 * Consumption of records, processing and production of new records on a given key [K] is done atomically
 * (that is, within a single _transaction_).  However, records for different keys may be batched up to
 * improve performance.
 */
interface StateAndEventSubscription<K, S, E> : SubscriptionBase

/**
 * This subscription should be used when consuming records from a compacted topic
 * (see https://kafka.apache.org/documentation.html#compaction).  [CompactedSubscription] differs from
 * [Subscription] in that it:
 *
 *     - guarantees that a record for every valid key in the topic will be provided
 *     - each record provided will be the most recent version of that record
 *
 * The subscription will initially provide the current, most up-to-date state (snapshot) for the topic; then
 * will provide subsequent updates.
 *
 * For more details on how the feed updates will be provided see [CompactedProcessor].
 */
interface CompactedSubscription<K : Any, V : Any> : Subscription<K, V> {
    /**
     *  Queries the topic values for the most recent value [V] of the given [key].
     *
     *  This is not thread-safe! It will be safer to call this from within the [CompactedProcessor] provided
     *  to the subscription in order to ensure thread safety.
     *
     *  @param key the topic key for a given state
     *  @return the current value for the given key, or null if it's not available
     */
    fun getValue(key: K): V?
}

