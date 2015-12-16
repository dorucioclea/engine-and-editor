package com.unifina.signalpath.charts

import com.unifina.utils.testutils.ModuleTestHelper
import spock.lang.Specification

class HeatmapSpec extends Specification {

	Heatmap module

	def setup() {
		module = new Heatmap()
		module.init()
		module.configure([
			uiChannel: [id: "heatmap"],
		])
	}

	void "heatmap sends correct data to uiChannel"() {
		when:
		Map inputValues = [
			latitude: [132.521, 42.452, -42.452, 0].collect {it?.doubleValue()},
			longitude: [164.001, 1.333, -1.333, 0].collect {it?.doubleValue()},
			value: [10, -5, 5, 0].collect {it?.doubleValue()}
		]
		Map outputValues = [:]
		Map channelMessages = [
			heatmap: [
				[t: "p", l: 132.521D, g: 164.001D, v: 10.0D],
				[t: "p", l: 42.452D, g: 1.333D, v: -5D],
				[t: "p", l: -42.452D, g: -1.333D, v: 5D],
				[t: "p", l: 0D, g: 0D, v: 0D],
			]
		]

		then:
		new ModuleTestHelper.Builder(module, inputValues, outputValues)
			.uiChannelMessages(channelMessages)
			.test()
	}
}