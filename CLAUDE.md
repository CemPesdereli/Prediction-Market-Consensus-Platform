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

Aynı endpoint `redeemable=true` ile çağrılırsa **sonuçlanmış (kapanmış)** pozisyonları
döner — `cashPnl`/`percentPnl` bu durumda o bahisten elde edilen gerçek kâr/zararı
taşıyor (Kapanmış Bahisler Detay Görünümü bölümüne bakınız).

"Ortak bahis" tanımı: aynı `conditionId`'ye sahip olmak yeterli sayılıyor; Yes/No
farklı taraflarda olmaları da ortaklık sayılır ama `outcome` alanıyla ayrıca gösterilir.
Eşik: `min-common-holders` (varsayılan **3**, önceden 2'ydi — 2 kişilik "ortaklık"
gürültülü/anlamsız sinyal üretiyordu, 3'e çıkarıldı) farklı cüzdan.

**`avgPrice` alanının anlamı (canlı API'de doğrulandı, varsayım değil):** `/positions`
cevabındaki `avgPrice`, o trader'ın **o pozisyondaki hâlihazırda elinde tuttuğu
hisseler için** hisse-adedi-ağırlıklı ortalama alış fiyatı — yani `sum(hisse × fiyat) /
sum(hisse)`. Birden fazla farklı fiyattan alım yapmış (satış yapmamış) üç gerçek top-20
cüzdanı/marketi için `/activity?type=TRADE` ile tek tek alım (BUY) kayıtları çekilip elle
ağırlıklı ortalama hesaplandı ve üçünde de API'nin `avgPrice` değeriyle (4 ondalık
haneye kadar) birebir eşleşti. Kısmi satışı olan bir pozisyonda (`totalBought > size`)
`avgPrice × size ≈ initialValue` de doğrulandı — yani satıştan sonra kalan pozisyon için
de tutarlı kalıyor (weighted-average cost yöntemi). Bu alan **kişiye özel**: aynı
markette aynı taraf (`outcome`) üzerinde olsalar bile iki farklı trader'ın `avgPrice`'ı
o markete ne zaman/hangi fiyattan girdiklerine göre birbirinden tamamen farklı olabilir
— `ConsensusMarket.HolderDetail.avgPrice` bu yüzden holder bazında (trader başına ayrı
ayrı) tutuluyor, market için tek bir ortalama hesaplanmıyor. Biz bu alanı kendimiz
hesaplamıyoruz, Polymarket'in döndürdüğü değeri olduğu gibi taşıyoruz
(`PositionDto.avgPrice()` → `ActivePosition.avgPrice()` → `HolderDetail.avgPrice()`).

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

**5) Teorik min/max aralığı (yeni karar):** `weightedConsensusPercent` tek bir sayı
olduğu için "bu marketi kaç kişi tuttuğu" bilgisini ROI ağırlığı bağlamında
yorumlamayı zorlaştırıyor — aynı `holderCount` çok farklı skorlara denk gelebilir
(en zayıf ROI'li k kişi mi, en güçlü ROI'li k kişi mi tuttu). Bunu göstermek için
`ConsensusMarket`'e `minPossiblePercent` / `maxPossiblePercent` eklendi:

```
k = holderCount (bu marketi tutan farklı cüzdan sayısı)
kohort ağırlıkları büyükten küçüğe sıralanır
maxPossiblePercent = sum(en yüksek k ağırlık) / totalCohortWeight * 100
minPossiblePercent = sum(en düşük k ağırlık)  / totalCohortWeight * 100
```

Yorum: "k kişi tutsaydı, kim oldukları en iyi/en kötü ihtimalle bu marketin skoru
şu aralıkta olurdu" — gerçek `weightedConsensusPercent` her zaman bu aralığın
içinde kalır (matematiksel invariant, `ConsensusServiceTest` içinde de doğrulanıyor).
Frontend'de `WeightGauge` bar'ının arkasında soluk bir bant, kartta da
"%min – %max aralığında" metni olarak gösteriliyor.

## Kapanmış Bahisler Detay Görünümü (yeni karar)

Ana sayfa (aktif/açık pozisyonları gösteren) sadece "hâlâ açık" ortak marketleri
gösteriyordu; top-20'nin **sonuçlanmış** ortak bahislerinde kimin haklı çıktığını
görmek için ayrı bir detay görünümü eklendi.

- Frontend'de aktif bahisler sayfasındaki header'a **"Son 3 günde kapananları
  göster"** butonu eklendi (`App.jsx`). Butona basılınca sayfa aynı kalıyor,
  altına `ClosedBetsPanel` bileşeni ekleniyor (ayrı bir route değil).
- Backend: `GET /api/closed-bets?category=X` → `ClosedConsensusService.getClosedConsensus()`.
  Trader listesi DB'deki en son senkronize edilmiş leaderboard'dan geliyor
  (`ConsensusRepositoryPort`), ama **kapanmış pozisyonlar kalıcı olarak
  saklanmıyor** — `PositionsPort.fetchClosedPositions()` (Polymarket
  `/positions?redeemable=true`) her istekte canlı çağrılıyor. Gerekçe: bu bir
  "detay göster" aksiyonu, periyodik sync gibi sürekli ihtiyaç duyulan bir veri
  değil; yeni bir DB tablosu/migration'a değmiyordu.
- `polymarket.closed-window-days` (varsayılan **3**) config'i ile pencere
  ayarlanabiliyor; `endDate` bu pencerenin içinde olan (`now - N gün` ile `now`
  arası) pozisyonlar dahil ediliyor. Aynı `min-common-holders` (3) eşiği burada
  da uygulanıyor — "ortak bahis" tanımı aktif/kapanmış arasında tutarlı.
- `ClosedConsensusMarket` weighted skor **hesaplamıyor** (bu sadece aktif
  consensus için anlamlı) — bunun yerine her market için holder bazında
  `outcome` (Yes/No), `percentPnl` (ROI) ve `cashPnl` ($ kâr/zarar) dökümü +
  markete toplam kâr/zarar (`totalCashPnl`) gösteriyor. Amaç: "top-20'den kim
  bu markette haklı çıktı, kim yanıldı, ne kazandı/kaybetti" sorusuna cevap.

**ÖNEMLİ bulgu (canlı API'de test edildi, varsayım değil):** `redeemable=true`
ile `/positions` sorgulamak **tek başına yeterli değil** ve ciddi bir yanlılık
üretiyor. Bir kazanan pozisyon claim edildiği (redeem) anda `/positions`
endpoint'inden **hangi `redeemable` değeriyle sorgularsan sorgula tamamen
kayboluyor** — aynı `conditionId` için `redeemable=true`, `redeemable=false`,
hatta filtresiz sorgu da boş dönüyor. Gerçek bir top-trader cüzdanıyla
doğrulandı: `redeemable=true` altında dönen pozisyonların tamamı `currentValue=0`,
`percentPnl≈-100` olan, hiç claim edilmemiş (değersiz olduğu için kimsenin
zahmet etmediği) **kaybedilen** bahislerdi. Yani sadece bu endpoint'i kullanmak
kazananları sistematik olarak dışarıda bırakıp sadece kaybedenleri gösterirdi.

Çözüm: iki kaynak birleştiriliyor (`ClosedConsensusService.getClosedConsensus()`):
1. `/positions?redeemable=true` → pratikte neredeyse tamamı **kaybedilen**,
   henüz claim edilmemiş bahisler. `cashPnl`/`percentPnl` burada API'den doğru
   geliyor.
2. `/activity?user=&type=REDEEM&start=<epoch>` → pratikte neredeyse tamamı
   **kazanılan** bahisler (kaybeden pozisyonu claim etmenin bir anlamı yok,
   değeri $0). Bu, claim edilen pozisyonun zincir üstündeki tek izi; `outcome`
   ve claim zamanı güvenilir ama `usdcSize` **brüt ödeme**, net kâr değil
   (maliyet bilgisi yoktu — bkz. aşağıdaki "Kazananlar için net kâr hesabı").

`PositionsPort`'a bu yüzden `fetchClosedPositions` (kaybedenler) ve
`fetchRedeemedPositions` (kazananlar) olmak üzere iki ayrı metot eklendi.

**Kazananlar için net kâr hesabı (sonradan eklendi — başta "orantısız maliyetli"
diye yapılmamıştı, kullanıcı isteğiyle geri dönüldü):** Her REDEEM kaydı için o
markette (`conditionId` + `outcome`) yapılmış **tüm** `TRADE` (BUY/SELL) geçmişi
`/activity?type=TRADE&market=<conditionId>` ile ayrıca çekiliyor
(`PolymarketPositionsAdapter.fetchTradeHistory`). Formül basit bir nakit-akışı
özdeşliği:

```
netKâr = (tüm SELL işlemlerinin usdcSize toplamı + redeem ödemesi)
         - (tüm BUY işlemlerinin usdcSize toplamı)
percentPnl = netKâr / toplamMaliyet * 100
```

Bu, pay-pay ağırlıklı-ortalama-maliyet (WAC) yöntemiyle hesaplanan sonuçla
matematiksel olarak birebir aynı çıkıyor (gerçek bir cüzdanın — opopv., "Taipei
37°C or higher" marketi, 36 işlemlik alım/satım geçmişi — hem WAC replay hem bu
basit toplam yöntemiyle çapraz doğrulandı, ikisi de $17.9865 net kâr verdi),
o yüzden pay bazında kronolojik replay'e gerek yok. `avgPrice` alanının
Polymarket'te gerçekten ağırlıklı-ortalama-maliyet mantığıyla çalıştığının
canlı veriyle doğrulanmış olması (yukarıdaki `avgPrice` bulgusuna bakınız) bu
iki yöntemin neden örtüştüğünün temelini oluşturuyor.

Güvenlik kontrolü: BUY-SELL'den hesaplanan net hisse sayısı, redeem kaydındaki
hisse sayısıyla (yaklaşık) eşleşmiyorsa (ör. `positions-limit` aşılıp eski
işlemler eksik geldiyse) hesap **güvenilir sayılmıyor** ve `cashPnl`/`percentPnl`
yine `null` bırakılıyor — yanlış bir sayı uydurmaktansa bilinmiyor demek tercih
ediliyor (bkz. `PolymarketPositionsAdapter.toRedeemedClosedPosition`). N kazanan
pozisyon için gereken N ek HTTP çağrısı, isteği yavaşlatmamak adına paralel
çalıştırılıyor (`TRADE_HISTORY_CONCURRENCY = 8`). Bu artık gerçek bir sayı
olduğu için frontend'de "—" yerine "N kazandı" için de "bilinen toplam kâr"
gösteriliyor (kaybedenlerdeki "bilinen toplam zarar" ile simetrik).

**Kapsam sınırı (canlı veride ölçüldü):** WEATHER kategorisinde 693 kazanan
holder satırının 398'i (~%57) için net kâr hesaplanabildi, 295'i güvenlik
kontrolüne takılıp `null` kaldı. Sebebi araştırıldı: bu null'lar neredeyse hep
`negativeRisk: true` olan (birden çok karşılıklı-dışlayan kovaya bölünmüş,
weather marketlerinde çok yaygın bir yapı) marketlerde çıkıyor. Bu tip
marketlerde trader'lar **MERGE/CONVERT** işlemleriyle bir kovadaki hisseyi
başka bir kovaya aktarabiliyor; bu, `/activity?type=TRADE` listesinde hiç
görünmüyor (ne BUY ne SELL). Sonuç: BUY-SELL'den hesaplanan hisse sayısı
redeem'deki gerçek hisse sayısından fazla çıkıyor (görülen bir örnekte 236.9
hisse alınmış ama sadece 75.0 hisse redeem edilmiş — aradaki ~162 hisse başka
bir kovaya MERGE/CONVERT edilmiş), güvenlik kontrolü bunu doğru şekilde
yakalayıp `null` bırakıyor. Bu bir bug değil, kasıtlı bir tercih: MERGE/CONVERT
zincirini de takip etmek kapsamı ciddi büyütürdü, o yüzden "hesaplanamayanlar
için null" ilkesi (yanlış sayı yerine bilinmiyor demek) burada da korunuyor.

**İkinci bulgu (gerçek veriyle UI testinde ortaya çıktı):** `ClosedPosition`/
`HolderOutcome`'da sadece `outcome` (Yes/No/Up/Down/oyuncu adı -- hangi tarafı
seçtiği) vardı, **kazanıp kazanmadığı** ayrıca gösterilmiyordu -- kullanıcı
sonucu anlayamadı. Ayrıca top-20 kohortunun "ortak" kapanmış bahisleri pratikte
neredeyse hep **hepsi kazanmış** kümeler çıkıyor (beklenmedik değil: consensus
tezinin ta kendisi -- birden fazla başarılı traderın hemfikir olduğu bahisler
isabetli çıkma eğiliminde), bu da eski `totalCashPnl` alanının çoğu zaman
"+0$" göstermesine yol açıyordu (bilinmeyen kazanç null→0 sayılınca) ve bu
"kimse kazanmadı/kaybetmedi" gibi yanlış okunuyordu.

Çözüm: `ClosedPosition`'a ve `HolderOutcome`'a açık bir `boolean won` alanı
eklendi (kaynağa göre kesin: `fetchClosedPositions`→`false`,
`fetchRedeemedPositions`→`true`). Market seviyesindeki tek `totalCashPnl`
alanı **kaldırıldı** -- frontend artık holder listesinden "N kazandı · M
kaybetti" + "bilinen toplam kâr"/"bilinen toplam zarar" (holder'ların gerçek
cashPnl'i taraf bazında toplanarak) türetiyor. Her holder satırında ayrı bir
"Sonuç" (KAZANDI/KAYBETTİ) rozeti var, "Seçim" (outcome) nötr/bilgilendirici
olarak ayrı gösteriliyor.

**Üçüncü bulgu (asıl kök neden -- kullanıcı üretimde "hepsi kazandı görünüyor"
diye fark etti, ham veriyle doğrulandı):** Yukarıdaki iki düzeltmeden sonra bile
**gerçek üretim verisinde tek bir kaybeden bile görünmüyordu** (101 WEATHER
marketinin 101'i de "hepsi kazandı"). Sebep clustering/consensus tezi değil,
düz bir **parse bug'ıydı**: `/positions` endpoint'inin `endDate` alanı saat
içermeyen düz tarih formatında geliyor (`"2026-07-23"`), REDEEM aktivitesinden
sentezlediğimiz `endDate` ise tam ISO instant (`"2026-07-23T23:47:43Z"`).
`ClosedConsensusService.isWithinWindow()` sadece `Instant.parse()` kullanıyordu
-- bu, bare-date formatında `DateTimeParseException` fırlatıyor, kod bunu
sessizce yutup pozisyonu "pencere dışı" sayıp atıyordu. Sonuç: `/positions?redeemable=true`
kaynaklı (yani pratikte kaybedenlerin **tamamı**) sessizce eleniyordu, sadece
REDEEM kaynaklı (kazananlar) pencereden geçiyordu -- consensus kümeleri o
yüzden hep "hepsi kazandı" çıkıyordu.

Gerçek bir cüzdanla (AndreaPirlo, WEATHER top-20) doğrulandı: "Will the highest
temperature in London be 27°C on July 23?" marketinde `cashPnl: -23.6017`
değeriyle gerçek bir kaybı vardı, ham API'de duruyordu, ama `endDate: "2026-07-23"`
(saatsiz) olduğu için uygulamada hiç görünmüyordu. Düzeltme:
`ClosedConsensusService.parseEndDate()` önce `Instant.parse()` dener, başarısız
olursa `LocalDate.parse(endDate).atStartOfDay(ZoneOffset.UTC).toInstant()` ile
bare-date'i de parse eder. `ClosedConsensusServiceTest`'e bu spesifik regresyonu
(bare-date `endDate`'li kaybedenlerin pencereden geçmesi gerektiğini) doğrulayan
bir test eklendi.

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
