package com.mikle.lab1.controller;


import com.mikle.lab1.dto.AuthRequest;
import com.mikle.lab1.dto.AuthResponse;
import com.mikle.lab1.service.AuthService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Аутентификация", description = "Регистрация и получение JWT")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @Operation(summary = "Войти и получить JWT")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request){
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "Зарегистрироваться и получить JWT",
            description = "Имя должно быть уникальным; пароль — не более 72 байт UTF-8.")
    @PostMapping("/create")
    public ResponseEntity<AuthResponse> create(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.create(request));
    }

}
