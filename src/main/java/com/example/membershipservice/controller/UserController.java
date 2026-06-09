package com.example.membershipservice.controller;

import com.example.membershipservice.model.entity.User;
import com.example.membershipservice.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public User createUser(@RequestBody CreateUserRequest request) {
        return userRepository.save(new User(request.name(), request.email()));
    }

    public record CreateUserRequest(String name, String email) {}
}
