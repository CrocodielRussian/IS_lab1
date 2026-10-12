package com.mikle.lab1.service;

import com.mikle.lab1.dto.AuthRequest;
import com.mikle.lab1.dto.AuthResponse;
import com.mikle.lab1.model.User;
import com.mikle.lab1.repository.AuthRepository;
import com.mikle.lab1.security.JwtGenerator;
import com.mikle.lab1.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AuthRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtGenerator jwtGenerator;
    private final PasswordEncoder passwordEncoder;

    public AuthResponse login(AuthRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadCredentialsException("Invalid credentials");
        }
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        var user = (UserDetailsImpl) authentication.getPrincipal();
        return new AuthResponse(jwtGenerator.generateToken(user), user.getId(), user.getUsername());
    }

    public AuthResponse create(AuthRequest request) {
        String username = request.username();
        String password = request.password();
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request");
        }
        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }

        var user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            if (userRepository.existsByUsername(username)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
            }
            throw exception;
        }

        UserDetailsImpl userDetails = new UserDetailsImpl(user);
        String jwt = jwtGenerator.generateToken(userDetails);

        return new AuthResponse(jwt, user.getId(), user.getUsername());
    }
}
