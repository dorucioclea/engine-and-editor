package com.unifina.signalpath.utils;

import com.streamr.client.protocol.message_layer.MessageID;
import com.streamr.client.protocol.message_layer.MessageRef;
import com.streamr.client.protocol.message_layer.StreamMessage;
import com.streamr.client.utils.Address;
import com.unifina.data.StreamPartitioner;
import com.unifina.domain.Stream;
import com.unifina.domain.User;
import com.unifina.utils.IdGenerator;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
/**
 * Generates StreamMessage chains by maintaining a state of the last message reference per
 * streamId-streamPartition-publisherId-msgChainId.
 */
public class MessageChainUtil implements Serializable {
	private Map<String,Long> previousTimestamps = new HashMap<>();
	private Map<String,Long> previousSequenceNumbers = new HashMap<>();

	private Long userId;
	private String publisherId;
	private String msgChainId;
	private transient User user;

	public MessageChainUtil(Long userId) {
		this.userId = userId;
		this.msgChainId = IdGenerator.getShort();
	}

	public MessageChainUtil(String publisherId) {
		this.publisherId = publisherId;
		this.msgChainId = IdGenerator.getShort();
	}

	private User getUser() {
		if (user == null) {
			user = User.getViaJava(userId);
		}
		return user;
	}

	private long getNextSequenceNumber(String key, long timestamp) {
		Long previousTimestamp = previousTimestamps.get(key);
		if (previousTimestamp == null || previousTimestamp != timestamp) {
			return 0L;
		}
		Long previousSequenceNumber = previousSequenceNumbers.get(key);
		return previousSequenceNumber == null ? 0L : previousSequenceNumber + 1;
	}

	private MessageRef getPreviousMessageRef(String key) {
		Long previousTimestamp = previousTimestamps.get(key);
		Long previousSequenceNumber = previousSequenceNumbers.get(key);
		if (previousTimestamp == null || previousSequenceNumber == null) {
			return null;
		}
		return new MessageRef(previousTimestamp, previousSequenceNumber);
	}

	public StreamMessage getStreamMessage(Stream stream, Date timestampAsDate, Map content){
		int streamPartition = StreamPartitioner.partition(stream, null);
		String key = stream.getId()+streamPartition;
		long timestamp = timestampAsDate.getTime();
		long sequenceNumber = getNextSequenceNumber(key, timestamp);
		String pid = null;
		if (this.userId != null) {
			pid = getUser().getPublisherId();
		}
		if (this.publisherId != null) {
			pid = this.publisherId;
		}
		MessageID msgId = new MessageID(stream.getId(), streamPartition, timestamp, sequenceNumber, new Address(pid), msgChainId);
		MessageRef prevMsgRef = this.getPreviousMessageRef(key);
		StreamMessage msg = new StreamMessage(msgId, prevMsgRef, content);
		previousTimestamps.put(key, timestamp);
		previousSequenceNumbers.put(key, sequenceNumber);
		return msg;
	}
}
