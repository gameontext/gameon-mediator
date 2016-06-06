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
package net.wasdev.gameon.mediator.kafka;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

@ApplicationScoped
public class KafkaRxCDIBridge {

    private final Set<LinkedBlockingQueue<GameOnEvent>> events = new CopyOnWriteArraySet<LinkedBlockingQueue<GameOnEvent>>();

    public KafkaRxCDIBridge() {
    }

    public void processRecord(@Observes GameOnEvent event) {
        System.out.println("Rx CDI Bridge [" + this.hashCode() + "] saw CDI event: " + event.toString() + " sending to "
                + (events.size()) + " queues.");
        for (LinkedBlockingQueue<GameOnEvent> queue : events) {
            try {
                queue.add(event);
            } catch (IllegalStateException e) {
                // if the queue is full..
                System.out.println("Rx CDI Bridge [" + this.hashCode() + "] tried to add to queue id ["
                        + queue.hashCode() + "] but it was full.");
            }
        }
    }

    public void addDataProvider(LinkedBlockingQueue<GameOnEvent> instance) {
        System.out.println("Rx CDI Bridge [" + this.hashCode() + "] adding queue id [" + instance.hashCode()
                + "] there are now " + (events.size() + 1) + " queues known");
        events.add(instance);
    }

    public void removeDataProvider(LinkedBlockingQueue<GameOnEvent> instance) {
        System.out.println("Rx CDI Bridge [" + this.hashCode() + "] removing queue id [" + instance.hashCode()
                + "] there are now " + (events.size() - 1) + " queues known");
        events.remove(instance);
    }
}
