package com.r2s.user;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class HelloController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello from User Service";
    }
}