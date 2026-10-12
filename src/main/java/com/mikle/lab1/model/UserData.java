package com.mikle.lab1.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Data
@Entity
@Table(name = "data")
public class UserData {
    @Id
    private UUID id;

    @org.hibernate.annotations.ColumnDefault("'Без названия'")
    @Column(nullable = false)
    private String name = "Без названия";

    @Column(nullable = false)
    private String description;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

}
