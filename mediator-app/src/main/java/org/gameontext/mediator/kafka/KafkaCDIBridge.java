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
package org.gameontext.mediator.kafka;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Destroyed;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.inject.Inject;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.gameontext.mediator.Log;

/**
 * A simple bean that obtains a kakfa consumer, subscribes to a topic via the
 * consumer and polls for messages on the topic, for each message, the bean
 * emits a cdi event. This bean is self-creating by monitoring the Initialized
 * event for the ApplicationScope
 */
@ApplicationScoped
public class KafkaCDIBridge {

    @Inject
    private KafkaConsumer<String, String> consumer;

    @Resource
    ManagedScheduledExecutorService executor;

    @SuppressWarnings({ "rawtypes" })
    private ScheduledFuture pollingThread;

    public void init(@Observes @Initialized(ApplicationScoped.class) Object init) {
        Runnable r = new Runnable() {
            public void run() {
                ConsumerRecords<String, String> records = null;
                synchronized (this) {
                    if (consumer != null) {
                        records = consumer.poll(100);
                    }
                }
                if (records != null && !records.isEmpty()) {
                    BeanManager bm = CDI.current().getBeanManager();
                    for (ConsumerRecord<String, String> record : records) {
                    	Log.log(Level.FINEST, this, "CDI Event firing..");
                        bm.fireEvent(new GameOnEvent(record.offset(), record.topic(), record.key(), record.value()));
                        Log.log(Level.FINEST, this, "CDI Event fired.");
                    }
                }
            }
        };

        Log.log(Level.FINEST, this, "CDI Registering Polling Thread.");
        pollingThread = executor.scheduleWithFixedDelay(r, 100, 100, TimeUnit.MILLISECONDS);

        List<String> topics = Arrays.asList(new String[] { "gameon", "playerEvents", "siteEvents" });
        Log.log(Level.FINEST, this, "CDI Subscribing to topics : "+topics);
        consumer.subscribe(topics);
    }

    public void destroy(@Observes @Destroyed(ApplicationScoped.class) Object init) {
    	Log.log(Level.FINEST, this, "CDI Shutting down kafka polling thread");
        pollingThread.cancel(true);
        Log.log(Level.FINEST, this, "CDI Closing kafka consumer.");
        consumer.close();
    }

}
