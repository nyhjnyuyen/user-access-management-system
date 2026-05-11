package com.r2s.user;

import com.r2s.core.entity.Role;
import com.r2s.core.event.UserActivatedEvent;
import com.r2s.core.event.UserDeletedEvent;
import com.r2s.core.event.UserRegisteredEvent;
import com.r2s.user.consumer.UserEventConsumer;
import com.r2s.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
public class UserEventConsumerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserEventConsumer userEventConsumer;

    @Test
    @DisplayName("handleUserRegistered_createUserProjection")
    void handleUserRegistered_createUserProjection() {
        UserRegisteredEvent event = new UserRegisteredEvent();
        event.setUsername("john");
        event.setEmail("john@example.com");
        event.setRole(Role.ROLE_USER);

        userEventConsumer.handleUserRegistered(event);
        verify(userService).createUserProjection(event);
        verifyNoMoreInteractions(userService);
    }

    @Test
    @DisplayName("handleUserActivated_updateUserProjection")
    void handleUserActivated_updateUserProjection() {
        UserActivatedEvent event = new UserActivatedEvent();
        event.setUsername("john");
        event.setEnabled(true);

        userEventConsumer.handleUserActivated(event);

        verify(userService).updateUserProjection("john",true);
        verifyNoMoreInteractions(userService);
    }

    @Test
    @DisplayName("handleUserDeleted calls deleteUserProjection")
    void handleUserDeleted_callsDeleteUserProjection() {
        UserDeletedEvent event = new UserDeletedEvent();
        event.setUsername("john");

        userEventConsumer.handleUserDeleted(event);

        verify(userService).deleteUserProjection("john");
        verifyNoMoreInteractions(userService);
    }
}
