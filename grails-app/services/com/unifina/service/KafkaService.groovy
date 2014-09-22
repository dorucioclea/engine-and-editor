package com.unifina.service

import grails.converters.JSON
import groovy.transform.CompileStatic

import java.nio.ByteBuffer
import java.nio.ByteOrder

import kafka.producer.ProducerConfig

import org.codehaus.groovy.grails.commons.GrailsApplication

import com.unifina.domain.data.FeedFile
import com.unifina.domain.data.Stream
import com.unifina.domain.task.Task
import com.unifina.kafkaclient.UnifinaKafkaConsumer
import com.unifina.kafkaclient.UnifinaKafkaMessage
import com.unifina.kafkaclient.UnifinaKafkaMessageHandler
import com.unifina.kafkaclient.UnifinaKafkaProducer
import com.unifina.task.KafkaCollectTask
import com.unifina.utils.TimeOfDayUtil


class KafkaService {

	UnifinaKafkaProducer producer = null
	GrailsApplication grailsApplication

	@CompileStatic
    void sendMessage(String channelId, Object key, String message, boolean isJson=true) {
		if (producer == null) {
			Properties props = ((ConfigObject)grailsApplication.config["unifina"]["kafka"]).toProperties()
			ProducerConfig producerConfig = new ProducerConfig(props)
			producer = new UnifinaKafkaProducer(props)
		}

		if (isJson)
			producer.sendJSON(channelId, key.toString(), System.currentTimeMillis(), message)
		else producer.sendString(channelId, key.toString(), System.currentTimeMillis(), message)
    }
	
	@CompileStatic
	void sendMessage(String channelId, Object key, Map message) {
		String str = (message as JSON).toString();
		sendMessage(channelId, key, str, true);
	}
	
	Date getFirstTimestamp(String topic) {
		UnifinaKafkaConsumer consumer = new UnifinaKafkaConsumer(grailsApplication.config.unifina.kafka.toProperties())
		Date firstTimestamp = null
		consumer.subscribe(topic, new UnifinaKafkaMessageHandler() {

			@Override
			public void handleMessage(UnifinaKafkaMessage msg) {
				if (firstTimestamp==null)
					firstTimestamp = new Date(msg.getTimestamp())
			}
		}, 0L)
		
		// Wait for the Kafka consumption to finish (or timeout)!
		long startTime = System.currentTimeMillis()
		while (firstTimestamp==null && System.currentTimeMillis()-startTime < 60L*1000L) {
			Thread.sleep(1000L);
		}
		consumer.close()
		return firstTimestamp
	}
	
	List<Task> createCollectTasks(Stream stream) {
		// The latest FeedFile indicates the last collected day
		FeedFile latest = FeedFile.withCriteria(uniqueResult:true) {
			eq("stream",stream)
			maxResults(1)
			order("endDate", "desc")
		}
		
		Date beginDate
		Date endDate
		if (latest) {
			beginDate = latest.beginDate+1
			endDate = latest.endDate+1
		}
		// If never collected, query the first timestamp from Kafka
		else {
			Map streamConfig = JSON.parse(stream.streamConfig)
			String topic = streamConfig.topic

			// If getFirstTimestamp(topic) returns null, there is nothing to be collected
			beginDate = getFirstTimestamp(topic)
			if (beginDate==null) {
				log.warn("Could not determine first timestamp for stream $stream.name, not collecting")
				return []
			}
			beginDate = TimeOfDayUtil.getMidnight(beginDate)
			endDate = new Date((beginDate+1).time-1)
		}
	
		// Create the task for each day up to today
		Date limit = TimeOfDayUtil.getMidnight(new Date())
		
		List tasks = []
		while (endDate.before(limit)) {
			Map config = KafkaCollectTask.getConfig(stream, beginDate, endDate)
			String configString = (config as JSON)
			
			// Check that the task does not exist already
			if (!Task.findByImplementingClassAndConfig(KafkaCollectTask.class.getName(), configString)) {
				Task task = new Task()
				task.available = true
				task.complete = false
				task.complexity = 0	
				task.category = "kafka-collect"
				task.config = (config as JSON).toString()
				task.implementingClass = KafkaCollectTask.class.name
				task.taskGroupId = UUID.randomUUID().toString()
				task.save(failOnError:true)
				tasks << task
			}
			beginDate += 1
			endDate += 1
		}
		return tasks
		
	}
}
