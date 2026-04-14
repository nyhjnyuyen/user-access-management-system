package com.r2s.user.consumer;

import com.r2s.core.entity.Role;
import com.r2s.core.entity.User;
import com.r2s.core.event.UserActivatedEvent;
import com.r2s.core.event.UserDeletedEvent;
import com.r2s.core.event.UserRegisteredEvent;
import com.r2s.user.repository.UserRepository;
import com.r2s.user.service.UserService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class UserEventConsumer {
    private final UserService userService;
    public UserEventConsumer(UserService userService) {
        this.userService = userService;
    }
    @KafkaListener(topics = "user-registered", groupId ="user-service-group")
    public void handleUserRegistered(UserRegisteredEvent event) {
        userService.createUserProjection(event);
    }

    @KafkaListener(topics = "user-activated", groupId = "user-service-group")
    public void handleUserActivated(UserActivatedEvent event) {
        userService.updateUserProjection(event.getUsername(), event.isEnabled());
    }
    @KafkaListener(topics = "user-deleted", groupId = "user-service-group")
    public void handleUserDeleted(UserDeletedEvent event) {
        userService.deleteUserProjection(event.getUsername());
    }

}
