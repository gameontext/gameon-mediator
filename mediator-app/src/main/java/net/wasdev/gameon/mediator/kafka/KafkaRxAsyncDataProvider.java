package net.wasdev.gameon.mediator.kafka;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import rx.Observable;
import rx.Observer;
import rx.functions.Action2;

public class KafkaRxAsyncDataProvider {

  @Inject
  KafkaRxCDIBridge cdiBridge;

  private LinkedBlockingQueue<GameOnEvent> events = new LinkedBlockingQueue<GameOnEvent>();

  private final AtomicBoolean registered = new AtomicBoolean(false);

  private RXCallback callback = new RXCallback();

  private class RXCallback implements Action2<Long,Observer<Observable<? extends GameOnEvent>>> {
    @Override
    public void call(final Long requested, final Observer<Observable<? extends GameOnEvent>> observer){
      System.out.println("Requested max of "+requested+" events. Queue id ["+events.hashCode()+"] waiting for event.");
       try{
          //this will block until an event is placed on the queue.
          GameOnEvent gameOnEvent=null;
          while(registered.get() && gameOnEvent==null){
            try{
              gameOnEvent = events.poll(1000, TimeUnit.MILLISECONDS);
            }catch(InterruptedException e){
              //no-op.
            }
          }

          //if we timedout and found ourselves no longer registered,
          //wipe the event queue and leave.
          if(!registered.get()){
            events.clear();
            return;
          }

          //we must have an event =)
          List<GameOnEvent> result = new ArrayList<GameOnEvent>();
          result.add(gameOnEvent);

          //assume requested is at least 1.
          //calc max items to return..
          long max = ((requested-1) < events.size()) ? (requested-1) : events.size();
          //if there are more events, copy up to 'max' of them.
          for(int i=0; i<max; i++){
            //this should not block, because we're under events.size();
            result.add(events.take());
          }

          //send our events to rxjava.
          observer.onNext(Observable.from(result));

      }catch(Exception e){
        System.out.println("RX Err "+e.getMessage());
        e.printStackTrace();
      }
    }
  }

  public KafkaRxAsyncDataProvider(){
  }

  @PostConstruct
  public void init(){
    registerWithBridge();
    System.out.println("Queue id ["+events.hashCode()+"] has local bridge instance of ["+cdiBridge.hashCode()+"]");
  }

  public Action2<Long,Observer<Observable<? extends GameOnEvent>>> getCallback(){
    return callback;
  }

  private void registerWithBridge(){
    if(registered.compareAndSet(false,true)){
      cdiBridge.addDataProvider(events);
    }
  }

  public void shutdown(){
    if(registered.compareAndSet(true,false)){
      cdiBridge.removeDataProvider(events);
    }
  }
}
