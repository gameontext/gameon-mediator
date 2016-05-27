package net.wasdev.gameon.mediator.kafka;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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

/**
A simple bean that obtains a kakfa consumer,
subscribes to a topic via the consumer and
polls for messages on the topic, for each message,
the bean emits a cdi event.
This bean is self-creating by monitoring the Initialized
event for the ApplicationScope
*/
@ApplicationScoped
public class KafkaCDIBridge {

	@Inject
	private KafkaConsumer<String, String> consumer;

	@Resource( lookup = "java:comp/DefaultManagedScheduledExecutorService" )
  ManagedScheduledExecutorService executor;

	@SuppressWarnings({ "rawtypes" })
	private ScheduledFuture pollingThread;

	public void init(@Observes @Initialized(ApplicationScoped.class) Object init) {
		Thread r = new Thread() {
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
						System.out.println("CDI Firing event.. ");
						bm.fireEvent(new GameOnEvent(record.offset(), record.topic(), record.key(), record.value()));
						System.out.println("CDI event fired");
					}
				}
			}
		};

		System.out.println("CDI Registering polling thread");
		pollingThread = executor.scheduleWithFixedDelay(r, 100, 100, TimeUnit.MILLISECONDS);

		List<String> topics = Arrays.asList(new String[] { "gameon","playerEvents","siteEvents" });
		System.out.println("CDI Subscribing to topics "+topics);
		consumer.subscribe(topics);
	}

  public void destroy(@Observes @Destroyed(ApplicationScoped.class) Object init) {
		System.out.println("CDI Shutting down kafka polling thread");
		pollingThread.cancel(true);
		System.out.println("CDI Closing kafka consumer.");
		consumer.close();
	}

}
