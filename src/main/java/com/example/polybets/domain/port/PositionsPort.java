package com.example.polybets.domain.port;

import com.example.polybets.domain.model.ActivePosition;

import java.util.List;

/**
 * Bir cüzdanın şu anki aktif (redeem edilmemiş) pozisyonlarını getiren port.
 */
public interface PositionsPort {

    List<ActivePosition> fetchActivePositions(String proxyWallet);
}
