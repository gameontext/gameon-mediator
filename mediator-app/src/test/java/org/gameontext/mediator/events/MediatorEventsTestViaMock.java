/*******************************************************************************
 * Copyright (c) 2016 IBM Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package org.gameontext.mediator.events;

import org.gameontext.mediator.events.MediatorEvents.PlayerEventHandler;
import org.gameontext.mediator.kafka.GameOnEvent;
import org.gameontext.mediator.kafka.KafkaRxJavaObservable;
import org.junit.Test;

import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func1;

public class MediatorEventsTestViaMock {

    @SuppressWarnings("unchecked")
    @Test
    public void TestEvents(@Mocked KafkaRxJavaObservable kafkarx, 
            @Mocked Subscription subscription, 
            @Mocked Observable<GameOnEvent> observable, 
            @Mocked PlayerEventHandler peh){
        
        MediatorEvents events = new MediatorEvents();
        
        Deencapsulation.setField(events, "kafka", kafkarx);
        
        new Expectations(){{ 
            kafkarx.consume(); result = observable;
            observable.filter((Func1<? super GameOnEvent, Boolean>) any); result = observable;
            observable.subscribe((Action1<? super GameOnEvent>) any); result = subscription;
        }};
        

        
        EventSubscription es = events.subscribeToPlayerEvents("Fish", peh);
        
        new Verifications(){{ 
            //grab the callback;
            Action1<? super GameOnEvent> subscribeCallback;
            observable.subscribe(subscribeCallback = withCapture());
            
            new Expectations(){{
                peh.playerUpdated((String)any,(String)any,(String)any);
            }};
            
            //test a player update event drives the right callback..
            GameOnEvent event1 = new GameOnEvent(0L, "playerEvents",
                    //This json has just enough to allow the method to work, if the player event content changes
                    //this json will need updating too. (Player event content is sent by player service.)
                    "Bubbles999","{\"type\":\"UPDATE\",\"player\":{\"name\":\"Bubbles\",\"favoriteColor\":\"blue\"}}");
            subscribeCallback.call(event1);
            new Verifications(){{
                peh.playerUpdated("Bubbles999", "Bubbles", "blue");
            }};
            
            //test a location update event drives the right callback..
            GameOnEvent event2 = new GameOnEvent(0L, "playerEvents","Bubbles999",
                    //This json has just enough to allow the method to work, if the player event content changes
                    //this json will need updating too. (Player event content is sent by player service.)
                    "{\"type\":\"UPDATE_LOCATION\",\"player\":{\"location\":\"Moon\"}}");
            subscribeCallback.call(event2);
            new Verifications(){{
                peh.locationUpdated("Bubbles999", "Moon");
            }};
        }};
        
        //test unsubscribe once.. 
        new Expectations(){{
            subscription.isUnsubscribed(); result = false;
        }};
        
        
        //unsubscribe.. 
        es.unsubscribe();
        
        new Verifications(){{ 
            subscription.unsubscribe(); times = 1;
        }};
        
        
        //test unsubscribe again when 'unsubscribed'.        
        new Expectations(){{
            subscription.isUnsubscribed(); result = true;
        }};
        
        
        //unsubscribe.. 
        es.unsubscribe();
        
        new Verifications(){{ 
            subscription.unsubscribe(); times = 0;
        }};
        
    }
}
