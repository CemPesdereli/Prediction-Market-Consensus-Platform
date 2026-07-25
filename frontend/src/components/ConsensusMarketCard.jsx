import WeightGauge from './WeightGauge.jsx'

function initials(name, wallet) {
  if (name) return name.slice(0, 2).toUpperCase()
  return wallet.slice(2, 4).toUpperCase()
}

function shortWallet(wallet) {
  return `${wallet.slice(0, 6)}…${wallet.slice(-4)}`
}

function formatUsd(value) {
  if (value == null) return null
  if (value > 0 && value < 1) return `$${value.toFixed(2)}`
  return `$${value.toLocaleString('en-US', { maximumFractionDigits: 0 })}`
}

function formatSharePrice(value) {
  if (value == null) return null
  return `$${value.toFixed(2)}`
}

export default function ConsensusMarketCard({ market }) {
  const plainPercent = market.cohortSize === 0 ? 0 : (market.holderCount * 100) / market.cohortSize
  const eventUrl = market.eventSlug ? `https://polymarket.com/event/${market.eventSlug}` : null

  return (
    <div className="rounded-2xl border border-ink-600 bg-ink-800/70 backdrop-blur-sm p-5 hover:border-amber-600/50 transition-colors">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          {eventUrl ? (
            <a href={eventUrl} target="_blank" rel="noreferrer"
               className="font-display font-semibold text-lg text-ash-100 hover:text-amber-400 transition-colors truncate block">
              {market.marketTitle}
            </a>
          ) : (
            <p className="font-display font-semibold text-lg text-ash-100 truncate">{market.marketTitle}</p>
          )}
          {market.endDate && (
            <p className="text-xs text-ash-500 mt-0.5">Bitiş: {market.endDate}</p>
          )}
        </div>

        <div className="text-right shrink-0">
          <div className="font-mono tabular-nums text-2xl font-bold text-amber-400 leading-none">
            {market.weightedConsensusPercent.toFixed(1)}%
          </div>
          <div className="font-mono tabular-nums text-xs text-ash-500 mt-1">
            {market.holderCount}/{market.cohortSize} kişi
          </div>
        </div>
      </div>

      <div className="mt-4">
        <WeightGauge
          weightedPercent={market.weightedConsensusPercent}
          plainPercent={plainPercent}
          minPercent={market.minPossiblePercent}
          maxPercent={market.maxPossiblePercent}
        />
      </div>

      <p className="mt-1.5 text-[11px] text-ash-500">
        %{market.minPossiblePercent.toFixed(1)} – %{market.maxPossiblePercent.toFixed(1)} aralığında
      </p>

      {market.sentimentYesPercent != null && (
        <div className="mt-3 flex items-center gap-2 text-xs">
          <span className="text-ash-500">Ağırlıklı yönelim:</span>
          <span className="font-mono font-semibold text-signal-yes">
            YES {market.sentimentYesPercent.toFixed(0)}%
          </span>
          <span className="text-ash-700">/</span>
          <span className="font-mono font-semibold text-signal-no">
            NO {(100 - market.sentimentYesPercent).toFixed(0)}%
          </span>
        </div>
      )}

      <div className="mt-4 flex flex-wrap gap-2">
        {market.holders.map((h) => (
          <div
            key={h.proxyWallet}
            className="flex items-center gap-2 rounded-full bg-ink-700/70 border border-ink-600 pl-1.5 pr-3 py-1"
            title={`${shortWallet(h.proxyWallet)} · ağırlık ${h.weight.toFixed(2)}x${h.currentValue != null ? ` · pozisyon değeri ${formatUsd(h.currentValue)}` : ''}${h.avgPrice != null ? ` · ort. alış fiyatı ${formatSharePrice(h.avgPrice)}/hisse` : ''}`}
          >
            <span className="flex items-center justify-center w-6 h-6 rounded-full bg-amber-500/20 text-amber-400 text-[10px] font-mono font-bold">
              {initials(h.userName, h.proxyWallet)}
            </span>
            <span className="text-xs text-ash-300 font-medium">{h.userName || shortWallet(h.proxyWallet)}</span>
            {(h.currentValue != null || h.avgPrice != null) && (
              <span className="text-[10px] font-mono font-semibold text-ash-400 whitespace-nowrap">
                {h.currentValue != null && formatUsd(h.currentValue)}
                {h.currentValue != null && h.avgPrice != null && ' · '}
                {h.avgPrice != null && (
                  <span className="text-ash-500">ort. {formatSharePrice(h.avgPrice)}</span>
                )}
              </span>
            )}
            {h.outcome && (
              <span className={`text-[10px] font-mono font-bold px-1.5 py-0.5 rounded ${
                h.outcome.toLowerCase() === 'yes'
                  ? 'bg-signal-yes/15 text-signal-yes'
                  : h.outcome.toLowerCase() === 'no'
                    ? 'bg-signal-no/15 text-signal-no'
                    : 'bg-ash-700/30 text-ash-300'
              }`}>
                {h.outcome}
              </span>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
