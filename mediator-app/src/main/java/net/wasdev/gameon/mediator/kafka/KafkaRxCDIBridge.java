package net.wasdev.gameon.mediator.kafka;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

@ApplicationScoped
public class KafkaRxCDIBridge {

  private final Set<LinkedBlockingQueue<GameOnEvent>> events = new CopyOnWriteArraySet<LinkedBlockingQueue<GameOnEvent>>();

  public KafkaRxCDIBridge(){
  }

  public void processRecord(@Observes GameOnEvent event) {
    System.out.println("Rx CDI Bridge ["+this.hashCode()+"] saw CDI event: " + event.toString() + " sending to "+(events.size())+" queues.");
    for(LinkedBlockingQueue<GameOnEvent> queue : events){
      try{
        queue.add(event);
      }catch(IllegalStateException e){
        //if the queue is full..
        System.out.println("Rx CDI Bridge ["+this.hashCode()+"] tried to add to queue id ["+queue.hashCode()+"] but it was full.");
      }
    }
  }

  public void addDataProvider(LinkedBlockingQueue<GameOnEvent> instance){
    System.out.println("Rx CDI Bridge ["+this.hashCode()+"] adding queue id ["+instance.hashCode()+"] there are now "+(events.size()+1)+" queues known");
    events.add(instance);
  }

  public void removeDataProvider(LinkedBlockingQueue<GameOnEvent> instance){
    System.out.println("Rx CDI Bridge ["+this.hashCode()+"] removing queue id ["+instance.hashCode()+"] there are now "+(events.size()-1)+" queues known");
    events.remove(instance);
  }
}
