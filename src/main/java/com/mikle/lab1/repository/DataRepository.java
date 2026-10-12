package com.mikle.lab1.repository;

import com.mikle.lab1.model.UserData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface DataRepository extends JpaRepository<UserData, UUID> {
    List<UserData> findAllByUserIdOrderById(UUID userId);
    Optional<UserData> findByIdAndUserId(UUID id, UUID userId);
}
