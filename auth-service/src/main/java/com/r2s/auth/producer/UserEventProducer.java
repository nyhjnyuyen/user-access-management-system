package com.r2s.auth.producer;

import com.r2s.core.event.UserActivatedEvent;
import com.r2s.core.event.UserDeletedEvent;
import com.r2s.core.event.UserRegisteredEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    public UserEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    public void publishedUserRegistered(UserRegisteredEvent event) {
        kafkaTemplate.send("user-registered", event.getUsername(), event);
    }
    public void publishedUserActivated(UserActivatedEvent event) {
        kafkaTemplate.send("user-activated", event.getUsername(), event);
    }
    public void publishedUserDeleted(UserDeletedEvent event) {
        kafkaTemplate.send("user-deleted", event.getUsername(), event);
    }
}
