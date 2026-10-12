package com.mikle.lab1.controller;

import com.mikle.lab1.dto.DataResponse;
import com.mikle.lab1.dto.DataRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import com.mikle.lab1.security.UserDetailsImpl;
import com.mikle.lab1.service.DataService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@Tag(name = "Данные", description = "Записи текущего пользователя")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DataController {
    private final DataService dataService;

    @Operation(summary = "Создать свою запись",
            description = "Владелец определяется по JWT. Имя и описание обязательны, не более 255 символов каждое.")
    @PostMapping("/data/create")
    @ResponseStatus(HttpStatus.CREATED)
    public DataResponse create(@Valid @RequestBody DataRequest request,
                               @Parameter(hidden = true) @AuthenticationPrincipal UserDetailsImpl user) {
        return dataService.create(request, user.getId());
    }

    @Operation(summary = "Получить запись по UUID",
            description = "Отсутствующая или чужая запись возвращает 404.")
    @GetMapping("/description/{id}")
    public DataResponse getDescription(@PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal UserDetailsImpl user) {
        return dataService.getDescriptionById(id, user.getId());
    }

    @Operation(summary = "Получить список своих записей")
    @GetMapping("/data")
    public List<DataResponse> allData(@Parameter(hidden = true) @AuthenticationPrincipal UserDetailsImpl user) {
        return dataService.getAllData(user.getId());
    }
}
