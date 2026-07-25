function shortWallet(wallet) {
  return `${wallet.slice(0, 6)}…${wallet.slice(-4)}`
}

function resultBadgeClass(won) {
  return won ? 'bg-signal-yes/15 text-signal-yes' : 'bg-signal-no/15 text-signal-no'
}

function pnlClass(value) {
  if (value == null) return 'text-ash-500'
  return value >= 0 ? 'text-signal-yes' : 'text-signal-no'
}

function formatPnl(value, suffix) {
  if (value == null) return '—'
  const sign = value >= 0 ? '+' : ''
  return `${sign}${value.toFixed(suffix === '%' ? 1 : 0)}${suffix}`
}

function marketSummary(holders) {
  const winners = holders.filter((h) => h.won)
  const losers = holders.filter((h) => !h.won)
  const knownLoss = losers.reduce((sum, h) => sum + (h.cashPnl ?? 0), 0)
  const knownProfit = winners.reduce((sum, h) => sum + (h.cashPnl ?? 0), 0)
  return { winnerCount: winners.length, loserCount: losers.length, knownLoss, knownProfit }
}

export default function ClosedBetsPanel({ markets, loading, error, windowDays }) {
  return (
    <div>
      <h2 className="font-display text-lg font-semibold text-ash-100 mb-1">
        Son {windowDays} günde kapanan ortak bahisler
      </h2>
      <p className="text-xs text-ash-500 mb-3">
        Kazananların net kârı, o marketteki tüm alım/satım geçmişi yeniden
        oluşturularak hesaplanıyor. Bu hesap güvenilir çıkmazsa (ör. çok sayıda
        işlem geçmişi varsa) "—" gösterilir; kaybedenlerde gösterilen rakamlar
        her zaman tam doğrudur.
      </p>

      {loading && (
        <div className="text-ash-500 font-mono text-sm">Yükleniyor…</div>
      )}

      {error && (
        <div className="rounded-lg border border-signal-no/30 bg-signal-no/5 text-signal-no text-sm px-4 py-3 mb-4">
          Hata: {error}
        </div>
      )}

      {!loading && !error && markets.length === 0 && (
        <div className="rounded-2xl border border-dashed border-ink-600 text-ash-500 text-sm px-6 py-10 text-center">
          Bu kategoride son {windowDays} günde kapanmış ortak bahis bulunamadı.
        </div>
      )}

      <div className="grid gap-4">
        {markets.map((m) => {
          const { winnerCount, loserCount, knownLoss, knownProfit } = marketSummary(m.holders)
          return (
            <div
              key={m.conditionId}
              className="rounded-2xl border border-ink-600 bg-ink-800/70 backdrop-blur-sm p-5"
            >
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <p className="font-display font-semibold text-ash-100 truncate">{m.marketTitle}</p>
                  {m.endDate && (
                    <p className="text-xs text-ash-500 mt-0.5">Kapanış: {m.endDate}</p>
                  )}
                </div>
                <div className="text-right shrink-0">
                  <div className="font-mono tabular-nums text-sm font-bold whitespace-nowrap">
                    <span className="text-signal-yes">{winnerCount} kazandı</span>
                    <span className="text-ash-700"> · </span>
                    <span className="text-signal-no">{loserCount} kaybetti</span>
                  </div>
                  {winnerCount > 0 && (
                    <div className="font-mono tabular-nums text-xs text-ash-500 mt-1">
                      bilinen toplam kâr: {formatPnl(knownProfit, '$')}
                    </div>
                  )}
                  {loserCount > 0 && (
                    <div className="font-mono tabular-nums text-xs text-ash-500 mt-1">
                      bilinen toplam zarar: {formatPnl(knownLoss, '$')}
                    </div>
                  )}
                </div>
              </div>

              <div className="mt-4 overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-left text-[10px] uppercase tracking-wider text-ash-500 border-b border-ink-700">
                      <th className="pb-2 font-medium">Trader</th>
                      <th className="pb-2 font-medium">Seçim</th>
                      <th className="pb-2 font-medium">Sonuç</th>
                      <th className="pb-2 font-medium text-right">Ağırlık</th>
                      <th className="pb-2 font-medium text-right">ROI</th>
                      <th className="pb-2 font-medium text-right">Kâr/Zarar</th>
                    </tr>
                  </thead>
                  <tbody>
                    {m.holders.map((h) => (
                      <tr key={h.proxyWallet} className="border-b border-ink-700/50 last:border-0">
                        <td className="py-2 text-ash-300 font-medium">
                          {h.userName || shortWallet(h.proxyWallet)}
                        </td>
                        <td className="py-2 text-ash-500 text-xs">{h.outcome || '—'}</td>
                        <td className="py-2">
                          <span className={`text-[10px] font-mono font-bold px-1.5 py-0.5 rounded ${resultBadgeClass(h.won)}`}>
                            {h.won ? 'KAZANDI' : 'KAYBETTİ'}
                          </span>
                        </td>
                        <td
                          className="py-2 text-right font-mono tabular-nums text-amber-400"
                          title="Kohort içindeki ROI-normalize ağırlık (1.0x-3.0x)"
                        >
                          {h.weight.toFixed(2)}x
                        </td>
                        <td className={`py-2 text-right font-mono tabular-nums ${pnlClass(h.percentPnl)}`}>
                          {formatPnl(h.percentPnl, '%')}
                        </td>
                        <td className={`py-2 text-right font-mono tabular-nums ${pnlClass(h.cashPnl)}`}>
                          {formatPnl(h.cashPnl, '$')}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
