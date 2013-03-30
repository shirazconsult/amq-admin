package com.nordija.activemq;

public class NoSuchMessageException extends RuntimeException {
	private String messageId;
	private String queue;
	
	public NoSuchMessageException(String messageId, String queue) {
		super();
		this.messageId = messageId;
		this.queue = queue;
	}
	
	public NoSuchMessageException(String errMsg, String messageId, String queue) {
		super(errMsg);
		this.messageId = messageId;
		this.queue = queue;		
	}

	public String getMessageId() {
		return messageId;
	}

	public void setMessageId(String messageId) {
		this.messageId = messageId;
	}

	public String getQueue() {
		return queue;
	}

	public void setQueue(String queue) {
		this.queue = queue;
	}

}
