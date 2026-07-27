import { useEffect, useState, useCallback } from 'react'
import { fetchWatchedBets, createWatchedBet, cancelWatchedBet } from '../api.js'

function extractSlug(input) {
  const trimmed = input.trim()
  if (!trimmed) return trimmed
  try {
    const url = new URL(trimmed)
    const parts = url.pathname.split('/').filter(Boolean)
    return parts[parts.length - 1] || trimmed
  } catch {
    return trimmed
  }
}

function statusBadgeClass(status) {
  if (status === 'TRIGGERED') return 'bg-amber-500/15 text-amber-400'
  if (status === 'CANCELLED') return 'bg-ash-700/30 text-ash-300'
  return 'bg-signal-yes/15 text-signal-yes'
}

function statusLabel(status) {
  if (status === 'TRIGGERED') return 'TETİKLENDİ'
  if (status === 'CANCELLED') return 'İPTAL'
  return 'İZLENİYOR'
}

function formatCents(value) {
  if (value == null) return '—'
  return `${(value * 100).toFixed(1)}¢`
}

export default function WatchedBetsPage() {
  const [watchedBets, setWatchedBets] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const [marketSlug, setMarketSlug] = useState('')
  const [outcome, setOutcome] = useState('YES')
  const [entryCents, setEntryCents] = useState('')
  const [targetCents, setTargetCents] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState(null)

  const load = useCallback(() => {
    setLoading(true)
    setError(null)
    fetchWatchedBets()
      .then(setWatchedBets)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  async function handleSubmit(e) {
    e.preventDefault()
    setFormError(null)

    const entry = parseFloat(entryCents)
    const target = parseFloat(targetCents)
    if (!marketSlug.trim() || Number.isNaN(entry) || Number.isNaN(target)) {
      setFormError('Tüm alanları doldurun.')
      return
    }

    setSubmitting(true)
    try {
      await createWatchedBet({
        marketSlug: extractSlug(marketSlug),
        outcome,
        entryPrice: entry / 100,
        targetPrice: target / 100,
      })
      setMarketSlug('')
      setEntryCents('')
      setTargetCents('')
      load()
    } catch (e) {
      setFormError(e.message)
    } finally {
      setSubmitting(false)
    }
  }

  async function handleCancel(id) {
    try {
      await cancelWatchedBet(id)
      load()
    } catch (e) {
      setError(e.message)
    }
  }

  const entryNum = parseFloat(entryCents)
  const targetNum = parseFloat(targetCents)
  const direction = !Number.isNaN(entryNum) && !Number.isNaN(targetNum) && targetNum !== entryNum
    ? (targetNum > entryNum
        ? `Kâr al: fiyat ${targetNum}¢'e çıkınca haber ver`
        : `Zarar kes: fiyat ${targetNum}¢'e inince haber ver`)
    : null

  return (
    <>
      <p className="text-sm text-ash-500 max-w-2xl mb-6">
        Girdiğin bir bahsi buradan izlemeye al: hangi hisseyi (YES/NO), kaç
        centten aldığını ve hangi fiyata gelince haber vermemi istediğini gir.
        15 dakikada bir anlık fiyatı kontrol edip hedefe ulaşınca Telegram
        üzerinden bildirim gönderirim.
      </p>

      <form
        onSubmit={handleSubmit}
        className="rounded-2xl border border-ink-600 bg-ink-800/70 backdrop-blur-sm p-5 mb-8 grid gap-4"
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="flex flex-col gap-1 sm:col-span-2">
            <span className="text-xs text-ash-500">Market URL'si ya da slug</span>
            <input
              value={marketSlug}
              onChange={(e) => setMarketSlug(e.target.value)}
              placeholder="https://polymarket.com/event/... ya da will-x-happen"
              className="bg-ink-900 border border-ink-600 text-ash-100 text-sm rounded-lg px-3 py-2
                         focus:outline-none focus:ring-2 focus:ring-amber-500/60"
            />
          </label>

          <label className="flex flex-col gap-1">
            <span className="text-xs text-ash-500">Hisse</span>
            <select
              value={outcome}
              onChange={(e) => setOutcome(e.target.value)}
              className="bg-ink-900 border border-ink-600 text-ash-100 text-sm rounded-lg px-3 py-2
                         focus:outline-none focus:ring-2 focus:ring-amber-500/60"
            >
              <option value="YES">YES</option>
              <option value="NO">NO</option>
            </select>
          </label>

          <div className="grid grid-cols-2 gap-4">
            <label className="flex flex-col gap-1">
              <span className="text-xs text-ash-500">Giriş fiyatı (¢)</span>
              <input
                type="number" min="0.1" max="99.9" step="0.1"
                value={entryCents}
                onChange={(e) => setEntryCents(e.target.value)}
                placeholder="42"
                className="bg-ink-900 border border-ink-600 text-ash-100 text-sm rounded-lg px-3 py-2
                           focus:outline-none focus:ring-2 focus:ring-amber-500/60"
              />
            </label>
            <label className="flex flex-col gap-1">
              <span className="text-xs text-ash-500">Hedef fiyat (¢)</span>
              <input
                type="number" min="0.1" max="99.9" step="0.1"
                value={targetCents}
                onChange={(e) => setTargetCents(e.target.value)}
                placeholder="70"
                className="bg-ink-900 border border-ink-600 text-ash-100 text-sm rounded-lg px-3 py-2
                           focus:outline-none focus:ring-2 focus:ring-amber-500/60"
              />
            </label>
          </div>
        </div>

        {direction && <p className="text-xs text-ash-500">{direction}</p>}
        {formError && <p className="text-xs text-signal-no">{formError}</p>}

        <button
          type="submit"
          disabled={submitting}
          className="justify-self-start text-sm font-medium rounded-lg px-4 py-2 bg-amber-500 text-ink-950
                     hover:bg-amber-400 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          {submitting ? 'Ekleniyor…' : 'Alarmı kur'}
        </button>
      </form>

      {loading && <div className="text-ash-500 font-mono text-sm">Yükleniyor…</div>}

      {error && (
        <div className="rounded-lg border border-signal-no/30 bg-signal-no/5 text-signal-no text-sm px-4 py-3 mb-6">
          Hata: {error}
        </div>
      )}

      {!loading && !error && watchedBets.length === 0 && (
        <div className="rounded-2xl border border-dashed border-ink-600 text-ash-500 text-sm px-6 py-14 text-center">
          Henüz bir fiyat alarmı kurmadın.
        </div>
      )}

      <div className="grid gap-3">
        {watchedBets.map((w) => (
          <div
            key={w.id}
            className="rounded-2xl border border-ink-600 bg-ink-800/70 backdrop-blur-sm p-4 flex items-center justify-between gap-4 flex-wrap"
          >
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                {w.eventSlug ? (
                  <a
                    href={`https://polymarket.com/event/${w.eventSlug}`}
                    target="_blank" rel="noreferrer"
                    className="font-display font-semibold text-ash-100 hover:text-amber-400 transition-colors truncate"
                  >
                    {w.marketTitle}
                  </a>
                ) : (
                  <p className="font-display font-semibold text-ash-100 truncate">{w.marketTitle}</p>
                )}
                <span className={`text-[10px] font-mono font-bold px-1.5 py-0.5 rounded ${
                  w.outcome === 'Yes' ? 'bg-signal-yes/15 text-signal-yes' : 'bg-signal-no/15 text-signal-no'
                }`}>
                  {w.outcome.toUpperCase()}
                </span>
              </div>
              <p className="text-xs text-ash-500 mt-1 font-mono tabular-nums">
                giriş {formatCents(w.entryPrice)} · hedef {formatCents(w.targetPrice)} · anlık {formatCents(w.lastCheckedPrice)}
              </p>
            </div>

            <div className="flex items-center gap-3 shrink-0">
              <span className={`text-[10px] font-mono font-bold px-2 py-1 rounded ${statusBadgeClass(w.status)}`}>
                {statusLabel(w.status)}
              </span>
              {w.status === 'ACTIVE' && (
                <button
                  onClick={() => handleCancel(w.id)}
                  className="text-xs font-medium rounded-lg px-3 py-1.5 bg-ink-700/70 border border-ink-600 text-ash-300
                             hover:border-signal-no/50 hover:text-signal-no transition-colors"
                >
                  İptal et
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </>
  )
}
