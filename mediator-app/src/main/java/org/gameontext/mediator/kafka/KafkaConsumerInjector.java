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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Level;

import javax.annotation.Resource;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.gameontext.mediator.Log;

/**
 * Builds a kafkaconsumer with no subscriptions..
 */
public class KafkaConsumerInjector {

    @Resource(lookup = "kafkaUrl")
    private String kafkaUrl;

    @Produces
    public KafkaConsumer<String, String> expose(InjectionPoint injection) {
        Log.log(Level.FINEST, this, "Building kafka for url " + kafkaUrl + " for class " + injection.getBean().getBeanClass().getName());

        if (System.getProperty("java.security.auth.login.config") == null) {
            Log.log(Level.FINEST, this, "Fudging JAAS property.");
            System.setProperty("java.security.auth.login.config", "");
        }

        Log.log(Level.FINEST, this, "Building Consumer.");
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaUrl);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "kafka." + injection.getBean().getBeanClass().getName());
        consumerProps.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        consumerProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");

        return new KafkaConsumer<String, String>(consumerProps);
    }
}
