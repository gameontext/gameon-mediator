package org.gameontext.mediator.kafka;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.Assert;
import org.junit.Test;

import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;

public class CDIBridgeTest {
    
    @SuppressWarnings("rawtypes")
    @Mocked CDI cdi;
    @Mocked BeanManager bm;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testEmitEvents(@Mocked ManagedScheduledExecutorService executor, 
            @Mocked KafkaConsumer<String,String> consumer,
            @Mocked ScheduledFuture future, @Mocked ConsumerRecord<String,String> record1, @Mocked ConsumerRecord<String,String> record2){
        
        KafkaCDIBridge kcdib = new KafkaCDIBridge();
        
        Deencapsulation.setField(kcdib,"consumer",consumer);
        Deencapsulation.setField(kcdib,"executor",executor);
        
        Map<TopicPartition, List<ConsumerRecord<String,String>>> data = new HashMap<>();
        List<ConsumerRecord<String,String>> records = new ArrayList<>();
        records.add(record1);
        records.add(record2);
        data.put(new TopicPartition("playerEvents", 1), records);
        ConsumerRecords<String, String> cr = new ConsumerRecords<String,String>(data);
        
        new Expectations() {{
           executor.scheduleWithFixedDelay((Runnable) any,anyLong,anyLong,(TimeUnit)any); result = future;
           consumer.subscribe((List<String>)any);
           record1.offset(); result=0L;
           record2.offset(); result=1L;
           record1.key(); result="stilettos";
           record2.key(); result="wedge";
           record1.topic(); result="playerEvents";
           record2.topic(); result="playerEvents";
           record1.value(); result="leather";
           record2.value(); result="patent";
        }};
        
        kcdib.init(null);
        
        new Verifications() {{
            //retrieve and invoke the nested thread..
            Runnable r;
            executor.scheduleWithFixedDelay(r = withCapture(), anyLong, anyLong, (TimeUnit)any);
            consumer.subscribe((List<String>)any);
            
            new Expectations() {{
                CDI.current(); result = cdi;
                cdi.getBeanManager(); result = bm;
                consumer.poll(anyLong); result = cr;
            }};
            
            r.run();
            
            new Verifications() {{
                List<Object> events = new ArrayList<>();
                
                //verify that 2 fire event calls are made.
                
                bm.fireEvent(withCapture(events)); times = 2;
                
                Assert.assertEquals(2, events.size());
                Assert.assertEquals(GameOnEvent.class, events.get(0).getClass());
                Assert.assertEquals(GameOnEvent.class, events.get(1).getClass());
                GameOnEvent e1 = (GameOnEvent)events.get(0);
                GameOnEvent e2 = (GameOnEvent)events.get(1);
                
                
            }};
            
            
        }};
        
        new Expectations() {{
            future.cancel(anyBoolean);
            consumer.close();
        }};
        
        kcdib.destroy(null);
        
    }
    
}

