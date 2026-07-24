# Prediction Market Intelligence Platform (Polymarket Consensus Engine)

Bu dosya, bu proje üzerinde çalışan her Claude Code oturumunun başlangıç bağlamıdır.
Yeni bir oturuma başlarken bu dosyayı oku, güncel durumu ve kararları buradan al.

## Proje Ne Yapıyor

Polymarket'te seçilen bir kategorinin (örn. WEATHER) **aylık leaderboard**'undaki ilk 20
trader'ın, aynı piyasada (`conditionId`) hâlâ **aktif** (sonuçlanmamış) pozisyon tuttuğu
ortak marketleri tespit edip bir "consensus" raporu üretiyor. Amaç: "en başarılı 20 kişi
şu an ortaklaşa nereye bahis oynuyor" sorusuna cevap vermek.

CV amacıyla, kurumsal seviyede (temiz mimari + test + CI/CD) bir backend projesi olarak
tasarlanıyor. Bu bir "Polymarket parser" değil, ileride başka platformlar (Kalshi,
Manifold, PredictIt) da eklenebilecek şekilde tasarlanan bir **Prediction Market
Intelligence Platform**'un ilk adımı.

## Kesinleşmiş Mimari Kararlar

- **"Hafif" (pragmatic) Hexagonal / Ports & Adapters** — tam DDD ciddiyetinde değil,
  tek Maven modülü içinde katman bazlı paket ayrımı:
  - `domain` — düz POJO'lar (Trader, Position, Market, ConsensusResult). Spring/JPA/
    Jackson annotation'ı **yok**.
  - `application` — use-case servisleri (LeaderboardService, ConsensusService, ReportService).
    Sadece port interface'lerine bağımlı, implementasyon detayı bilmez.
  - `adapter` — dış dünyaya bakan her şey: Polymarket WebClient adapter'ı, JPA repository
    adapter'ı, REST controller'lar, (ileride) Telegram adapter'ı.
  - Port örnekleri: `MarketDataPort`, `LeaderboardPort`, `ConsensusRepositoryPort`.
- **ArchUnit** ile bu katman sınırları otomatik test edilecek (örn. "domain paketi
  Spring'e bağımlı olamaz", "adapter'dan application'a bağımlılık olamaz").
- Neden hexagonal: Consensus mantığı Polymarket'e bağımlı olmasın; ileride başka platform
  eklemek yeni bir adapter yazmaktan ibaret olsun. "API kırılırsa diye" savunmacı bir
  gerekçe değil, test edilebilirlik ve genişletilebilirlik gerekçesi.
- **Veritabanı**: PostgreSQL + Flyway (migration yönetimi baştan var, H2'ye geri
  dönülmedi — CV'de daha iyi durduğu için bilinçli tercih).
- **Test stratejisi**: JUnit 5 + Mockito (unit) + WireMock (Polymarket API mock'lu
  entegrasyon testleri) + **Testcontainers** (gerçek Postgres container'ına karşı
  repository/entegrasyon testleri).
- **Docker Compose**: yerel Postgres + (ileride) uygulamanın kendisi için.
- **CI**: GitHub Actions (Faz 2'de eklenecek).

## Doğrulanmış Polymarket API Detayları (ÖNEMLİ — varsayım değil, test edildi)

Bazı kaynaklar "Polymarket resmi API'si aktif pozisyonları vermiyor" diyor —
**bu yanlış**, aşağıdaki iki endpoint gerçekten çalışıyor ve auth gerektirmiyor:

```
GET https://data-api.polymarket.com/v1/leaderboard
    ?category=WEATHER          (POLITICS, SPORTS, ESPORTS, CRYPTO, CULTURE,
                                 MENTIONS, WEATHER, ECONOMICS, TECH, FINANCE, OVERALL)
    &timePeriod=MONTH           (DAY, WEEK, MONTH, ALL)
    &orderBy=PNL
    &limit=20
→ [{ rank, proxyWallet, userName, vol, pnl, profileImage, xUsername, verifiedBadge }]

GET https://data-api.polymarket.com/positions
    ?user=0x...
    &redeemable=false           (= henüz sonuçlanmamış / aktif pozisyon)
    &limit=500
→ [{ proxyWallet, asset, conditionId, size, avgPrice, currentValue, cashPnl,
     curPrice, redeemable, title, slug, eventSlug, outcome, outcomeIndex, endDate }]
```

"Ortak bahis" tanımı: aynı `conditionId`'ye sahip olmak yeterli sayılıyor; Yes/No
farklı taraflarda olmaları da ortaklık sayılır ama `outcome` alanıyla ayrıca gösterilir.
Eşik: `min-common-holders` (varsayılan 2) farklı cüzdan.

## Önceki Sürüm (referans / yeniden kullanılacak kod)

`polymarket-common-bets` adında, düz katmanlı (hexagonal olmayan), H2 + Thymeleaf
kullanan çalışan bir MVP zaten var: `PolymarketDataApiClient` (WebClient ile yukarıdaki
iki endpoint'i çağırıyor), `LeaderboardSyncService` (@Scheduled ile periyodik senkron),
`CommonBetsService` (conditionId bazlı gruplama). Bu projeyi **sıfırdan yazmıyoruz**,
API client ve iş mantığını yeni paket yapısına (`domain`/`application`/`adapter`)
taşıyarak (refactor ederek) evrimleştiriyoruz.

## Weighted Consensus Algoritması (yeni karar)

Klasik "kaç kişi aynı markette" sayımına ek olarak, trader'ların **ROI'sine göre
ağırlıklı** bir consensus skoru da hesaplanacak. Rapor **her iki metriği de birlikte**
gösterecek (biri diğerini ezmiyor, karşılaştırma için ikisi de var).

**1) Trader ağırlığı (ROI proxy üzerinden):**

Polymarket API'sinde doğrudan "ROI" alanı yok; leaderboard'daki `pnl` (dönemsel kâr)
ve `vol` (işlem hacmi) üzerinden proxy hesaplanıyor:

```
roi_i = pnl_i / vol_i        // hacme göre verimlilik — "büyük hacimli ama düşük
                              //   marjlı" ile "küçük hacimli ama keskin" traderı ayırt eder
```

Top-20 içinde min-max normalize edilip 1.0x–3.0x aralığına sıkıştırılıyor:

```
norm_i   = (roi_i - min(roi)) / (max(roi) - min(roi))   // 0-1 arası
weight_i = 1.0 + 2.0 * norm_i                            // 1.0-3.0 arası
```

Gerekçe: top-20'ye girmiş olmak zaten bir taban değer taşısın (en kötü ROI'li bile
1.0x, sıfırlanmıyor), ama en iyi ROI'li kişi en fazla 3 katı ağırlıklı etkiye sahip
olsun (tek bir "balina" sonucu tek başına domine etmesin).

**2) Market bazında weighted consensus skoru:**

```
totalWeight        = Σ weight_i  (marketi tutan trader alt kümesi S için)
maxWeight          = Σ weight_i  (top-20'nin tamamı için)
weightedConsensus% = totalWeight / maxWeight * 100
```

**3) Weighted sentiment (YES/NO yönelimi):**

```
sentimentYes% = yesWeight / (yesWeight + noWeight) * 100
```

**4) Rapor çıktısı** her market için **hem** klasik `holderCount` / 20 **hem de**
`weightedConsensusPercent` + `sentimentYesPercent` gösterecek şekilde tasarlanacak
(`CommonBetDto`'ya bu alanlar eklenecek).

## Frontend Kararı (yeni karar — önceki Thymeleaf planından değişti)

Ayrı bir **React SPA** (Vite + Tailwind CSS) yazılacak, backend'in REST API'sini
(`/api/common-bets`, `/api/sync`) tüketecek. Thymeleaf tamamen kaldırılıyor — backend
saf bir REST API'ye dönüşüyor, frontend bağımsız bir proje/klasör (`frontend/` veya
ayrı repo) olarak geliştirilecek. Gerekçe: CV'de full-stack yeteneği göstermek + daha
temiz/modern bir görsel sonuç.

## Yol Haritası (Faz Planı)

- **Faz 1 (şu an burdayız)**: Hexagonal iskelet, domain modeli, portlar, Polymarket
  adapter'ı (önceki koddan taşınmış), **Weighted Consensus Engine** (yukarıdaki ROI
  ağırlıklı algoritma), PostgreSQL + Flyway, JUnit5 + Mockito + WireMock +
  Testcontainers testleri, Docker Compose, backend'i saf REST API'ye çevirme
  (Thymeleaf kaldırılıyor), ayrı **React SPA (Vite+Tailwind)** frontend.
- **Faz 2**: GitHub Actions CI, ROI'ye göre ağırlıklı consensus (başarılı trader'ların
  oyu daha ağır basar), historical tracking (consensus'un zaman içindeki değişimi).
- **Faz 3**: Telegram bot (günlük consensus bildirimi), basit dashboard.
- **Faz 4 (nice-to-have)**: Micrometer + Prometheus + Grafana, SonarQube, ikinci bir
  platform adapter'ı (Kalshi/Manifold) ile mimarinin gerçekten platform-agnostik
  olduğunu kanıtlamak.

## Konvansiyonlar

- Kod: İngilizce (sınıf/metot/değişken adları), yorumlar ve commit mesajları Türkçe
  olabilir (önceki projede bu şekildeydi, tutarlılık için devam).
- Paket kökü: `com.example.polybets` (önceki projeden devam, değişmedi).
- Java 21, Spring Boot 3.3+/3.5.
- README.md'yi güncel tutmayı unutma — her faz sonunda "şu ana kadar ne yapıldı"
  bölümü CV'de referans verilecek şekilde net olmalı.

## Şu Anki Durum / Sıradaki Adım

- Yerel geliştirme ortamı hazır: ProtonVPN kuruldu (Türkiye'den Polymarket erişimi
  ISS seviyesinde engelli, VPN ile ABD sunucusundan erişiliyor), önceki basit sürüm
  (`polymarket-common-bets`) uçtan uca test edildi ve **çalıştığı doğrulandı**
  (sync, `/api/common-bets`, H2 verisi hepsi doğrulandı).
- Weighted consensus algoritması ve React SPA frontend kararları netleşti (yukarıda).
- Sıradaki somut adım: projeyi **sıfırdan** (ama önceki API client mantığını taşıyarak)
  hexagonal paket yapısıyla kurmak:
  1. `pom.xml` güncellemesi — Postgres/Flyway/Testcontainers/ArchUnit/WireMock
     bağımlılıkları, Thymeleaf'in kaldırılması.
  2. `domain` / `application` / `adapter` paket yapısına geçiş.
  3. `ConsensusService`'e weighted hesaplama mantığının eklenmesi (`CommonBetDto`'ya
     `weightedConsensusPercent`, `sentimentYesPercent` alanları).
  4. `docker-compose.yml` (Postgres) + Flyway migration dosyaları.
  5. Ayrı `frontend/` klasöründe Vite+React+Tailwind SPA iskeleti, `/api/common-bets`
     tüketen basit bir tablo/kart görünümü.
