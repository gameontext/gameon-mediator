package net.wasdev.gameon.mediator.kafka;

public class GameOnEvent {
	private long offset;
	private String topic;
	private String key;
	private String value;

	public GameOnEvent(){
	}

	public GameOnEvent(long offset, String topic, String key, String value){
		this.offset = offset;
		this.topic = topic;
		this.key=key;
		this.value=value;
	}
	public long getOffset(){
		return offset;
	}
	public String getTopic(){
		return topic;
	}
	public String getKey(){
		return key;
	}
	public String getValue(){
		return value;
	}

	public String toString(){
		return "GameOnEvent["+this.hashCode()+"] offset:"+offset+" topic:"+topic+" key:"+key+" value:"+value;
	}
}
