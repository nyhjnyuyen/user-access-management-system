package com.r2s.auth;

import com.r2s.auth.producer.UserEventProducer;
import com.r2s.auth.repository.UserRepository;
import com.r2s.auth.scheduler.UserCleanupScheduler;
import com.r2s.core.entity.Role;
import com.r2s.core.entity.User;
import com.r2s.core.event.UserDeletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserCleanupSchedulerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserEventProducer producer;

    @InjectMocks
    private UserCleanupScheduler scheduler;

    private User makeUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@test.com");
        user.setPassword("12345678");
        user.setRole(Role.ROLE_USER);
        user.setEnabled(false);
        user.setCreatedAt(LocalDateTime.now().minusDays(3));
        return user;
    }

    @Test
    void deleteUnactivatedUsers_shouldProcessMoreThanOneBatch() {
        List<User> batch1 = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            batch1.add(makeUser("user" + i));
        }

        List<User> batch2 = new ArrayList<>();
        for (int i = 101; i <= 150; i++) {
            batch2.add(makeUser("user" + i));
        }

        when(userRepository.findByEnabledFalseAndCreatedAtBefore(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(batch1)
                .thenReturn(batch2)
                .thenReturn(Collections.emptyList());

        scheduler.deleteUnactivatedUsers();

        verify(userRepository, times(3))
                .findByEnabledFalseAndCreatedAtBefore(any(LocalDateTime.class), any(Pageable.class));

        verify(userRepository, times(2)).deleteAllInBatch(anyList());

        verify(producer, times(150)).publishedUserDeleted(any(UserDeletedEvent.class));
    }
}