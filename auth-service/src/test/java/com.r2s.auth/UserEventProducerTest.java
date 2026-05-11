package com.r2s.auth;

import com.r2s.auth.producer.UserEventProducer;
import com.r2s.core.event.UserActivatedEvent;
import com.r2s.core.event.UserDeletedEvent;
import com.r2s.core.event.UserRegisteredEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserEventProducerTest {
    @Mock
    private KafkaTemplate<String,Object> kafkaTemplate;

    @InjectMocks
    private UserEventProducer userEventProducer;

    @Test
    @DisplayName("publishedUserRegistered_sendsEventToKafka")
    void publishedUserRegistered_sendsEventToKafka() {
        UserRegisteredEvent event = mock(UserRegisteredEvent.class);
        when(event.getUsername()).thenReturn("john");

        userEventProducer.publishedUserRegistered(event);

        verify(kafkaTemplate).send("user-registered", "john", event);
        verify(event).getUsername();
        verifyNoMoreInteractions(kafkaTemplate, event);
    }

    @Test
    @DisplayName("publishedUserActivated_sendsEventToKafka")
    void publishedUserActivated_sendsEventToKafka() {
        UserActivatedEvent event = mock(UserActivatedEvent.class);
        when(event.getUsername()).thenReturn("john");

        userEventProducer.publishedUserActivated(event);

        verify(kafkaTemplate).send("user-activated", "john", event);
        verify(event).getUsername();
        verifyNoMoreInteractions(kafkaTemplate, event);
    }

    @Test
    @DisplayName("publishedUserDeleted_sendsEventToKafka")
    void publishedUserDeleted_sendsEventToKafka() {
        UserDeletedEvent event = mock(UserDeletedEvent.class);
        when(event.getUsername()).thenReturn("john");

        userEventProducer.publishedUserDeleted(event);

        verify(kafkaTemplate).send("user-deleted", "john", event);
        verify(event).getUsername();
        verifyNoMoreInteractions(kafkaTemplate, event);
    }

}
