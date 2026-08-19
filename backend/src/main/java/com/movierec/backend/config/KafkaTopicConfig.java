package com.movierec.backend.config;

import com.movierec.backend.event.MovieViewEventProducer;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares Kafka topics this app owns. {@link org.springframework.kafka.core.KafkaAdmin}
 * creates them automatically on startup if missing.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic movieViewEventsTopic() {
        return TopicBuilder.name(MovieViewEventProducer.TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
