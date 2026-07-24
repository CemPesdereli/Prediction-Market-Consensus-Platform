# Polymarket Consensus Platform

Polymarket'te seçilen bir kategorinin **aylık liderlik tablosundaki ilk 20 trader**'ının
hâlâ açık (aktif) pozisyon tuttuğu ortak marketleri tespit edip, traderların **ROI'sine
göre ağırlıklandırılmış** bir "akıllı para consensus" skoru üreten full-stack platform.

Backend: Java 21 / Spring Boot, hexagonal (ports & adapters) mimari, PostgreSQL + Flyway.
Frontend: React (Vite) + Tailwind, ayrı bir SPA olarak backend'in REST API'sini tüketir.

## Mimari

```
                         ┌────────────────────────┐
                         │   domain (saf Java)     │
                         │  Trader, ActivePosition, │
                         │  ConsensusMarket, ports  │
                         └───────────▲─────────────┘
                                     │ sadece portlara bağımlı
                         ┌───────────┴─────────────┐
                         │      application         │
                         │ LeaderboardSyncService,   │
                         │ ConsensusService (weighted│
                         │ consensus algoritması)    │
                         └───────────▲─────────────┘
                                     │
        ┌────────────────────────────┼────────────────────────────┐
        │                            │                            │
┌───────┴────────┐          ┌────────┴────────┐          ┌────────┴────────┐
│ adapter.out.    │          │ adapter.out.    │          │ adapter.in.web   │
│ polymarket      │          │ persistence     │          │ CommonBets       │
│ (WebClient →    │          │ (JPA → Postgres)│          │ Controller (REST)│
│ Data API)       │          │                 │          │                 │
└─────────────────┘          └─────────────────┘          └────────▲────────┘
                                                                    │
                                                          ┌─────────┴─────────┐
                                                          │  React SPA         │
                                                          │  (frontend/)       │
                                                          └────────────────────┘
```

Domain katmanı hiçbir framework'e (Spring, JPA, Jackson) bağımlı değil — bu, `HexagonalArchitectureTest`
(ArchUnit) ile her build'de otomatik doğrulanıyor. Consensus algoritmasının kalbi olan
`ConsensusService.calculate()` de saf bir fonksiyon (I/O yok), bu yüzden hiç mock'a
gerek kalmadan doğrudan unit test edilebiliyor.

## Weighted Consensus Algoritması

1. **ROI proxy**: `roi = pnl / vol` (Polymarket API'sinde direkt ROI alanı yok, dönemsel
   kâr/hacim oranı üzerinden yaklaşıklanıyor).
2. **Ağırlık normalizasyonu**: kohort (top-20) içinde min-max normalize edilip **[1.0, 3.0]**
   aralığına sıkıştırılıyor — en kötü ROI'li bile 1.0x taban ağırlık taşıyor (top-20'ye
   girmenin karşılığı), en iyi ROI'li en fazla 3 kat etkili oluyor (tek bir "balina" sonucu
   domine edemesin).
3. **Market skoru**: `weightedConsensusPercent = Σ(ağırlık, o marketi tutanlar) / Σ(ağırlık, tüm kohort) * 100`
4. **Yönelim (sentiment)**: `Yes/No` ağırlıklarının oranı, "bu market ROI-ağırlıklı olarak
   hangi yöne eğiliyor" sorusuna cevap veriyor.
5. Rapor **hem klasik headcount** (`holderCount`/`cohortSize`) **hem weighted skoru**
   birlikte gösteriyor — biri diğerini ezmiyor.

## Kurulum ve Çalıştırma

### 1) Postgres'i ayağa kaldır
```bash
docker compose up -d
```

### 2) Backend'i çalıştır
```bash
mvn spring-boot:run
```
Flyway migration'ları otomatik uygulanır, uygulama açılışta tüm kategoriler için ilk
senkronizasyonu tetikler (`@PostConstruct`).

> **Not (Türkiye'den erişim):** Polymarket API'sine Türkiye'den doğrudan erişim ISS
> seviyesinde engelli. Yerel geliştirme için ABD sunucusuna bağlı bir VPN (ör. Proton VPN)
> açık olmalı.

### 3) Frontend'i çalıştır
```bash
cd frontend
npm install
npm run dev
```
`http://localhost:5173` — Vite dev server, `/api/**` isteklerini otomatik olarak
`localhost:8080`'e proxy'liyor (bkz. `vite.config.js`).

### API uçları
- `GET /api/categories`
- `GET /api/common-bets?category=WEATHER`
- `POST /api/sync?category=WEATHER` (30 dakikalık zamanlanmış job'u beklemeden manuel tetikleme)

## Testler

```bash
mvn test
```

| Test | Ne doğrular |
|---|---|
| `HexagonalArchitectureTest` (ArchUnit) | Domain katmanı hiçbir framework'e/adapter'a bağımlı değil |
| `ConsensusServiceTest` | Weighted consensus algoritmasının matematiği (saf unit test, I/O yok) |
| `PolymarketLeaderboardAdapterTest` (WireMock) | API şeması değişse/hata dönse bile adapter'ın davranışı |
| `ConsensusRepositoryAdapterIT` (Testcontainers) | Gerçek PostgreSQL'e karşı kayıt/okuma, Flyway şeması |

## CV / Mülakat Konuşma Noktaları

- **Neden hexagonal?** Consensus mantığı Polymarket'e bağımlı değil; yarın başka bir
  platform (Kalshi, Manifold) eklemek yeni bir adapter yazmaktan ibaret. Bunu sadece
  iddia etmiyorum, ArchUnit testiyle her build'de otomatik doğruluyorum.
  - Belirlenen sınırlar: paketler katman bazlı (domain/application/adapter), portlar
    domain'de tanımlı interface'ler, implementasyonlar adapter'da; tam DDD/multi-modül
    ciddiyetinde değil — bilinçli olarak "pragmatic" seviyede tutuldu.
- **Neden Flyway + `ddl-auto: validate`?** Şema değişikliklerinin sürüm kontrolünde,
  gözden geçirilebilir migration dosyaları halinde olmasını istedim; Hibernate'in
  şemayı "otomatik tahmin etmesine" güvenmedim.
- **Neden ROI-ağırlıklı consensus?** Ham "kaç kişi aynı markette" sayımı, büyük ama
  vasat traderları küçük ama keskin traderlarla eşit sayar. ROI proxy'siyle ağırlıklandırma,
  "akıllı para nereye gidiyor" sorusuna daha isabetli bir cevap veriyor — ama ağırlığı
  [1,3] aralığında sınırlayarak tek bir aykırı değerin sonucu domine etmesini engelledim.
- **Test stratejisi**: pure-function unit test (mock yok) + WireMock (dış API sözleşmesi)
  + Testcontainers (gerçek Postgres) — üç farklı katmanın üç farklı test stratejisiyle
  doğrulanması.

## Yol Haritası

Bkz. `CLAUDE.md` — sonraki fazlar (GitHub Actions CI, Telegram bot, gözlemlenebilirlik).
