package com.example.polybets.domain.port;

import com.example.polybets.domain.model.ActivePosition;
import com.example.polybets.domain.model.ClosedPosition;

import java.time.Instant;
import java.util.List;

/**
 * Bir cüzdanın pozisyonlarını getiren port.
 */
public interface PositionsPort {

    List<ActivePosition> fetchActivePositions(String proxyWallet);

    /**
     * Henüz claim edilmemiş (redeem edilmemiş) ama sonuçlanmış pozisyonlar.
     * ÖNEMLİ: bunlar neredeyse tamamen KAYBEDİLEN bahislerdir -- kazanan
     * pozisyonlar claim edilir edilmez /positions'tan tamamen kayboluyor
     * (bkz. fetchRedeemedPositions). Bu yüzden tek başına "kapanmış bahisler"
     * için yeterli değil, kazananları görmek için ikisi birlikte kullanılmalı.
     */
    List<ClosedPosition> fetchClosedPositions(String proxyWallet);

    /**
     * {@code since} tarihinden sonra claim edilmiş (redeem edilmiş) pozisyonlar
     * -- pratikte neredeyse tamamı KAZANILAN bahisler (kaybeden pozisyonları
     * claim etmenin bir anlamı yok, değeri $0). cashPnl/percentPnl bilinmiyor
     * (null): net kârı hesaplamak için o markette yapılan tüm alım/satım
     * geçmişini toplamak gerekir, bu on-demand bir görünüm için orantısız
     * maliyetli; sadece kimin kazandığı (outcome) ve ne zaman claim ettiği
     * güvenilir şekilde gösteriliyor.
     */
    List<ClosedPosition> fetchRedeemedPositions(String proxyWallet, Instant since);
}
