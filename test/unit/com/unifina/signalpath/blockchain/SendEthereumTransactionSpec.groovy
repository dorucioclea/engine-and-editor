package com.unifina.signalpath.blockchain

import com.google.gson.Gson
import com.unifina.datasource.DataSource
import com.unifina.domain.signalpath.Canvas
import com.unifina.signalpath.SignalPath
import com.unifina.utils.Globals
import com.unifina.utils.testutils.ModuleTestHelper
import grails.test.mixin.Mock
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt
import org.web3j.protocol.core.methods.response.EthSendTransaction
import org.web3j.protocol.core.methods.response.TransactionReceipt
import spock.lang.Specification

@Mock(Canvas)
class SendEthereumTransactionSpec extends Specification {
	SendEthereumTransaction module

	def setup() {
		module = new SendEthereumTransaction() {
			Web3j getWeb3j() {
				return mockWeb3j;
			}
		}
		module.init()
		module.configure([
			params : [
				[name: "URL", value: "localhost"],
				[name: "verb", value: "GET"],
			]
		])
		def signalPath = new SignalPath(true)
		signalPath.setCanvas(new Canvas())
		module.setParentSignalPath(signalPath)
	}

	/** Mocked event queue. Works manually in tests, please call module.receive(queuedEvent) */
	def mockGlobals = Stub(Globals) {
		getDataSource() >> Stub(DataSource) {
			enqueueEvent(_) >> { feedEvent ->
				functionCallResult = feedEvent.content[0]
			}
		}
		isRealtime() >> true
	}

	// temporary storage for async transaction generated by AbstractHttpModule, passing from globals to mockClient
	SendEthereumTransaction.FunctionCallResult functionCallResult

	def logList = []
	def mockWeb3j = Stub(Web3j) {
		ethGetTransactionReceipt(_) >> Stub(Request) {
			send() >> Stub(EthGetTransactionReceipt) {
				getResult() >> Stub(TransactionReceipt) {
					getLogs() >> logList
				}
			}
		}
		ethSendRawTransaction(_) >> Stub(Request) {
			sendAsync() >> {
				thenAccept(_) >> { consumer ->
					EthSendTransaction tx
					consumer.accept(tx)
				}
			}
		}

		// helpers for handy mocking of different responses within same test
		def responseI = [].iterator()
		/*
		{
					def ret = response
					// array => iterate
					if (ret instanceof Iterable) {
						// end of array -> restart from beginning
						if (!responseI.hasNext()) {
							responseI = response.iterator()
						}
						ret = responseI.hasNext() ? responseI.next() : []
					}
					// closure => execute
					if (ret instanceof Closure) {
						ret = ret(request)
					}
					// convert into JSON if not String
					ret = ret instanceof String ? ret : new JsonBuilder(ret).toString()
					return new StringEntity(ret)
				}
		 */
	}

	static Map applyConfig = new Gson().fromJson('''
TODO: capture
''', Map.class)

	void "test"() {
		logList = []
		def inputs = [trigger: true]
		def outputs = [errors: []]
		expect:
		new ModuleTestHelper.Builder(module, inputs, outputs)
			.overrideGlobals { mockGlobals }
			.onModuleInstanceChange { newInstance -> module = newInstance }
			.test()
	}

}