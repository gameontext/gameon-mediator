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

public class MediatorEventsTestViaRxJava {
    private static String USERID = "Bubbles999";
    private static String TOPIC = "playerEvents";

    @Test
    public void TestMediatorEvents(@Mocked KafkaRxJavaObservable kafkarx,
                                   @Mocked PlayerEventHandler peh){
        MediatorEvents events = new MediatorEvents();
        
        //test a player update event drives the right callback..
        GameOnEvent event1 = new GameOnEvent(0L, TOPIC,USERID,
                //This json has just enough to allow the method to work, if the player event content changes
                //this json will need updating too. (Player event content is sent by player service.)
                "{\"type\":\"UPDATE\",\"player\":{\"name\":\"Bubbles\",\"favoriteColor\":\"blue\"}}");
        //test a location update event drives the right callback..
        GameOnEvent event2 = new GameOnEvent(0L, TOPIC,USERID,
                //This json has just enough to allow the method to work, if the player event content changes
                //this json will need updating too. (Player event content is sent by player service.)
                "{\"type\":\"UPDATE_LOCATION\",\"player\":{\"location\":\"Moon\"}}");
        //test non player events are ignored.
        GameOnEvent event3 = new GameOnEvent(0L, "fishEvents","FISHEVENTUSER",
                //This json has just enough to allow the method to work, if the player event content changes
                //this json will need updating too. (Player event content is sent by player service.)
                "{\"type\":\"UPDATE_LOCATION\",\"player\":{\"location\":\"FISHEVENT\"}}");
        //test events for a different player are ignored.
        GameOnEvent event4 = new GameOnEvent(0L, TOPIC,"NotBubbles999",
                //This json has just enough to allow the method to work, if the player event content changes
                //this json will need updating too. (Player event content is sent by player service.)
                "{\"type\":\"UPDATE_LOCATION\",\"player\":{\"location\":\"NOTBUBBLES\"}}");
        
        Observable<GameOnEvent> observable = Observable.just(event1,event2,event3,event4);
        
        Deencapsulation.setField(events, "kafka", kafkarx);
        
        new Expectations(){{ 
            kafkarx.consume(); result = observable;
            peh.playerUpdated((String)any,(String)any,(String)any);
            peh.locationUpdated((String)any, (String)any);
        }};
        
        EventSubscription es = events.subscribeToPlayerEvents(USERID, peh);
        
        new Verifications(){{ 
            peh.playerUpdated("Bubbles999", "Bubbles", "blue"); times=1;
            peh.locationUpdated("Bubbles999", "Moon");
        }};

        //unsubscribe.. 
        es.unsubscribe();

    }
}
