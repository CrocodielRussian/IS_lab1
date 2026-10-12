package com.mikle.lab1.service;

import com.mikle.lab1.dto.DataResponse;
import com.mikle.lab1.dto.DataRequest;
import com.mikle.lab1.model.UserData;
import com.mikle.lab1.repository.DataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DataService {
    private final DataRepository dataRepository;

    public DataResponse create(DataRequest request, UUID userId) {
        var data = new UserData();
        data.setId(UUID.randomUUID());
        data.setUserId(userId);
        data.setName(request.name());
        data.setDescription(request.description());
        return toResponse(dataRepository.save(data));
    }

    public List<DataResponse> getAllData(UUID userId) {
        return dataRepository.findAllByUserIdOrderById(userId).stream().map(this::toResponse).toList();
    }

    public DataResponse getDescriptionById(UUID id, UUID userId) {
        return dataRepository.findByIdAndUserId(id, userId).map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private DataResponse toResponse(UserData data) {
        return new DataResponse(data.getId(), data.getName(), data.getDescription());
    }
}
