package net.corda.flow.application.services.impl

import net.corda.flow.application.services.impl.interop.dispatch.buildDispatcher
import net.corda.flow.application.services.impl.interop.facade.FacadeReaders
import net.corda.flow.application.services.impl.interop.facade.FacadeRequestImpl
import net.corda.flow.application.services.impl.interop.facade.FacadeResponseImpl
import net.corda.flow.application.services.impl.interop.proxies.JacksonJsonMarshallerAdaptor
import net.corda.flow.application.services.impl.interop.proxies.getClientProxy
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.interop.facade.Facade
import net.corda.v5.application.interop.facade.FacadeId
import net.corda.v5.application.interop.facade.FacadeRequest
import net.corda.v5.application.interop.facade.FacadeResponse
import net.corda.v5.application.interop.FacadeService
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.LoggerFactory
import java.security.AccessController
import java.security.PrivilegedActionException
import java.security.PrivilegedExceptionAction

@Component(service = [FacadeService::class, UsedByFlow::class], scope = PROTOTYPE)
class FacadeServiceImpl @Activate constructor(
    @Reference(service = JsonMarshallingService::class)
    private val jsonMarshallingService: JsonMarshallingService,
    @Reference(service = FlowMessaging::class)
    private val flowMessaging: FlowMessaging,
) : FacadeService, UsedByFlow, SingletonSerializeAsToken {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Suspendable
    override fun <T : Any?> getFacade(facadeId: String?, expectedType: Class<T>?, alias: MemberX500Name?, interopGroup: String?): T {
        logger.info("Creating Proxy for: $facadeId, $expectedType, $alias, $interopGroup")
        val facade = facadeLookup(facadeId!!)
        val marshaller = JacksonJsonMarshallerAdaptor(jsonMarshallingService)
        val transportLayer = MessagingDispatcher(flowMessaging, jsonMarshallingService, alias!!, interopGroup!!)
        return facade.getClientProxy(marshaller, expectedType!!, transportLayer)
    }

    @Suspendable
    override fun dispatchFacadeRequest(target: Any?, request: String?): String {
        logger.info("Dispatching: ${target!!::class.java}, $request")
        val facadeRequest = jsonMarshallingService.parse(request!!, FacadeRequestImpl::class.java)
        val facade = facadeLookup(facadeRequest.facadeId)
        val marshaller = JacksonJsonMarshallerAdaptor(jsonMarshallingService)
        val dispatcher = target.buildDispatcher(facade, marshaller)
        val facadeResponse = dispatcher.invoke(facadeRequest)
        return jsonMarshallingService.format(facadeResponse)
    }

    @Suppress("UNUSED_PARAMETER")
    @Suspendable
    private fun facadeLookup(facadeId: String): Facade {
        return try {
            AccessController.doPrivileged(PrivilegedExceptionAction {
                FacadeReaders.JSON.read(
                    """{ 
                        "id": "/com/r3/tokens/sample/v1.0",
                        "commands": { 
                            "say-hello": {
                                "in": {
                                    "greeting": "string"
                                },
                                "out": {
                                    "greeting": "string"
                                }
                            },
                            "get-balance": {
                                "in": {
                                    "greeting": "string"
                                },
                                "out": {
                                    "greeting": "string"
                                }
                            }
                        }
                    }""".trimIndent()
                )
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }

    private fun facadeLookup(facadeId: FacadeId) : Facade = facadeLookup(facadeId.toString())
}

class MessagingDispatcher(private var flowMessaging: FlowMessaging, private val jsonMarshallingService: JsonMarshallingService,
    private val alias: MemberX500Name, val aliasGroupId: String) : (FacadeRequest) -> FacadeResponse {
    override fun invoke(request: FacadeRequest): FacadeResponse {
        val payload = jsonMarshallingService.format(request)
        val response = flowMessaging.callFacade(alias, aliasGroupId, request.facadeId.toString(), request.methodName, payload)
        return jsonMarshallingService.parse(response, FacadeResponseImpl::class.java)
    }
}
