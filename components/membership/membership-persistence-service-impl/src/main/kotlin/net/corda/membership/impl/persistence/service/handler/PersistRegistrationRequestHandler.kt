package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.impl.persistence.service.handler.RegistrationStatusHelper.toStatus
import net.corda.membership.lib.registration.RegistrationStatusExt.canMoveToStatus
import net.corda.orm.utils.transaction
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import javax.persistence.LockModeType

internal class PersistRegistrationRequestHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<PersistRegistrationRequest, Unit>(persistenceHandlerServices) {
    override val operation = PersistRegistrationRequest::class.java
    override fun invoke(context: MembershipRequestContext, request: PersistRegistrationRequest) {
        val registrationId = request.registrationRequest.registrationId
        logger.info("Persisting registration request with ID [$registrationId] to status ${request.status}.")
        var currentRegistrationRequest: RegistrationRequestEntity? = null
        transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            currentRegistrationRequest = em.find(
                RegistrationRequestEntity::class.java,
                registrationId,
                LockModeType.PESSIMISTIC_WRITE,
            )
            currentRegistrationRequest?.status?.toStatus()?.let {
                if (it == request.status) {
                    logger.info(
                        "Registration request [$registrationId] with status: $it is already persisted. Persistence request was discarded."
                    )
                } else if (!it.canMoveToStatus(request.status)) {
                    logger.info(
                        "Registration request [$registrationId] has status: $it can not move it to status ${request.status}"
                    )
                    // In case of processing persistence requests in an unordered manner we need to make sure the serial
                    // gets persisted. All other existing data of the request will remain the same.
                    if (request.status == RegistrationStatus.SENT_TO_MGM && currentRegistrationRequest!!.serial == null) {
                        val newSerial = request.registrationRequest.serial
                        logger.info("Updating request [$registrationId] serial to $newSerial")
                        em.merge(createEntityBasedOnPreviousEntity(currentRegistrationRequest!!, newSerial))
                    } else {
                        // do nothing
                    }
                } else {
                    logger.info(
                        "##- Updating existing registration request '{}' from '{}' to '{}'.",
                        currentRegistrationRequest!!.registrationId,
                        it,
                        request.status
                    )
                    val update = createEntityBasedOnRequest(request)
                    logger.info("Updated entity has serial ${update.serial}")
                    em.merge(update)
                }
            }
            return@transaction
        }

        if (currentRegistrationRequest == null) {
            persistNewEntity(context.holdingIdentity.toCorda(), request)
        }
    }

    private fun persistNewEntity(holdingIdentity: HoldingIdentity, request: PersistRegistrationRequest) {
        try {
            logger.info(
                "##- Persisting new registration request '{}' with status '{}'.",
                request.registrationRequest.registrationId,
                request.status
            )

            getEntityManager(holdingIdentity.shortHash).transaction {
                val now = clock.instant()
                with(request.registrationRequest) {
                    val q = it.createNativeQuery("INSERT INTO vnode_registration_request VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                        .setParameter(1, registrationId)
                        .setParameter(2, request.registeringHoldingIdentity.toCorda().shortHash.value)
                        .setParameter(3, request.status.toString())
                        .setParameter(4, now)
                        .setParameter(5, now)
                        .setParameter(6, memberContext.data.array())
                        .setParameter(7, memberContext.signature.publicKey.array())
                        .setParameter(8, memberContext.signature.bytes.array())
                        .setParameter(9, memberContext.signatureSpec.signatureName)
                        .setParameter(10, registrationContext.data.array())
                        .setParameter(11, registrationContext.signature.publicKey.array())
                        .setParameter(12, registrationContext.signature.bytes.array())
                        .setParameter(13, registrationContext.signatureSpec.signatureName)
                        .setParameter(14, "")
                        .setParameter(15, serial)

                    logger.info("##- Query is: ${q.parameters}")
                    q.executeUpdate()
                }
            }

            logger.info(
                "##- Persisted new registration request '{}' with status '{}'.",
                request.registrationRequest.registrationId,
                request.status
            )
        } catch (e: Exception) {
            logger.warn(
                "##- Registration request '{}' with status '{}' threw $e",
                request.registrationRequest.registrationId,
                request.status
            )
            throw e
        }
    }

    private fun createEntityBasedOnPreviousEntity(previousEntity: RegistrationRequestEntity, newSerial: Long): RegistrationRequestEntity {
        val now = clock.instant()
        with(previousEntity) {
            return RegistrationRequestEntity(
                registrationId = registrationId,
                holdingIdentityShortHash = holdingIdentityShortHash,
                status = status,
                created = created,
                lastModified = now,
                memberContext = memberContext,
                memberContextSignatureKey = memberContextSignatureKey,
                memberContextSignatureContent = memberContextSignatureContent,
                memberContextSignatureSpec = memberContextSignatureSpec,
                registrationContext = registrationContext,
                registrationContextSignatureKey = registrationContextSignatureKey,
                registrationContextSignatureContent = registrationContextSignatureContent,
                registrationContextSignatureSpec = registrationContextSignatureSpec,
                serial = newSerial,
            )
        }
    }

    private fun createEntityBasedOnRequest(request: PersistRegistrationRequest): RegistrationRequestEntity {
        val now = clock.instant()
        with(request.registrationRequest) {
            return RegistrationRequestEntity(
                registrationId = registrationId,
                holdingIdentityShortHash = request.registeringHoldingIdentity.toCorda().shortHash.value,
                status = request.status.toString(),
                created = now,
                lastModified = now,
                memberContext = memberContext.data.array(),
                memberContextSignatureKey = memberContext.signature.publicKey.array(),
                memberContextSignatureContent = memberContext.signature.bytes.array(),
                memberContextSignatureSpec = memberContext.signatureSpec.signatureName,
                registrationContext = registrationContext.data.array(),
                registrationContextSignatureKey = registrationContext.signature.publicKey.array(),
                registrationContextSignatureContent = registrationContext.signature.bytes.array(),
                registrationContextSignatureSpec = registrationContext.signatureSpec.signatureName,
                serial = serial,
            )
        }
    }
}
