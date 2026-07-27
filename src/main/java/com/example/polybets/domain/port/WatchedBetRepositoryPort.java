package com.example.polybets.domain.port;

import com.example.polybets.domain.model.WatchedBet;
import com.example.polybets.domain.model.WatchedBetStatus;

import java.util.List;
import java.util.Optional;

/**
 * Kullanıcının manuel fiyat alarmlarının kalıcı hale getirilmesi.
 */
public interface WatchedBetRepositoryPort {

    WatchedBet save(WatchedBet watchedBet);

    List<WatchedBet> findAll();

    List<WatchedBet> findByStatus(WatchedBetStatus status);

    Optional<WatchedBet> findById(Long id);
}
