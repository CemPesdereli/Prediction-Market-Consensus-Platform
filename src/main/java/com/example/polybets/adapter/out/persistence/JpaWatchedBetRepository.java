package com.example.polybets.adapter.out.persistence;

import com.example.polybets.adapter.out.persistence.entity.WatchedBetEntity;
import com.example.polybets.domain.model.WatchedBetStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface JpaWatchedBetRepository extends JpaRepository<WatchedBetEntity, Long> {

    List<WatchedBetEntity> findByStatus(WatchedBetStatus status);
}
