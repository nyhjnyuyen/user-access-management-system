package com.r2s.auth.scheduler;

import com.r2s.auth.repository.UserRepository;
import com.r2s.core.entity.User;
import com.r2s.core.event.UserDeletedEvent;
import com.r2s.auth.producer.UserEventProducer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
@Component
public class UserCleanupScheduler {

    public final UserRepository userRepository;
    public final UserEventProducer producer;

    public UserCleanupScheduler(UserRepository userRepository, UserEventProducer producer) {
        this.userRepository = userRepository;
        this.producer = producer;
    }

    @Scheduled(fixedRate = 3600000) //10000) //moi 1 tieng
    public void deleteUnactivatedUsers() {
        LocalDateTime diff = LocalDateTime.now().minusHours(48); //Seconds(10);

        List<User> users = userRepository.findByEnabledFalseAndCreatedAtBefore(diff);
        for (User user : users) {
            //gui event sand user-service
            producer.publishedUserDeleted(new UserDeletedEvent(user.getUsername()));

            userRepository.delete(user);
        }
    }
}
