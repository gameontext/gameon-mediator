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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import javax.annotation.Resource;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;

/**
 * Builds a kafkaconsumer with no subscriptions..
 */
public class KafkaConsumerInjector {

    @Resource(lookup = "kafkaUrl")
    private String kafkaUrl;

    @Produces
    public KafkaConsumer<String, String> expose(InjectionPoint injection) {
        System.out.println(
                "Building kafka for url " + kafkaUrl + " for class " + injection.getBean().getBeanClass().getName());

        if (System.getProperty("java.security.auth.login.config") == null) {
            System.out.println("Fudging jaas property.");
            System.setProperty("java.security.auth.login.config", "");
        }

        System.out.println("Building consumer.. ");
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaUrl);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "kafka." + injection.getBean().getBeanClass().getName());
        consumerProps.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        consumerProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");

        // this is a cheat, we need to enable ssl when talking to message
        // hub, and not to kafka locally. The easiest way to know which we are
        // running on, is to check how many hosts are in kafkaUrl.
        // Locally for kafka there'll only ever be one, and messagehub gives
        // us a whole bunch..
        boolean multipleHosts = kafkaUrl.indexOf(",") != -1;
        if (multipleHosts) {
            System.out.println("Initialising ssl consumer for kafka");
            consumerProps.put("security.protocol", "SASL_SSL");
            consumerProps.put("ssl.protocol", "TLSv1.2");
            consumerProps.put("ssl.enabled.protocols", "TLSv1.2");
            Path p = Paths.get(System.getProperty("java.home"), "lib", "security", "cacerts");
            consumerProps.put("ssl.truststore.location", p.toString());
            consumerProps.put("ssl.truststore.password", "changeit");
            consumerProps.put("ssl.truststore.type", "JKS");
            consumerProps.put("ssl.endpoint.identification.algorithm", "HTTPS");
        }

        return new KafkaConsumer<String, String>(consumerProps);
    }
}
