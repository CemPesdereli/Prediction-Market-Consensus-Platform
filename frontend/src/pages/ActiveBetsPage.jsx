import { useEffect, useState, useCallback } from 'react'
import { useOutletContext, Link } from 'react-router-dom'
import { fetchCommonBets, triggerSync } from '../api.js'
import ConsensusMarketCard from '../components/ConsensusMarketCard.jsx'
import { CLOSED_WINDOW_DAYS } from '../constants.js'

export default function ActiveBetsPage() {
  const { category } = useOutletContext()
  const [markets, setMarkets] = useState([])
  const [loading, setLoading] = useState(true)
  const [syncing, setSyncing] = useState(false)
  const [error, setError] = useState(null)

  const load = useCallback((cat) => {
    setLoading(true)
    setError(null)
    fetchCommonBets(cat)
      .then(setMarkets)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    load(category)
  }, [category, load])

  async function handleSync() {
    setSyncing(true)
    try {
      await triggerSync(category)
      await new Promise((r) => setTimeout(r, 500))
      load(category)
    } catch (e) {
      setError(e.message)
    } finally {
      setSyncing(false)
    }
  }

  return (
    <>
      <div className="flex items-center justify-between gap-4 flex-wrap mb-6">
        <p className="text-sm text-ash-500 max-w-2xl">
          Seçilen kategorinin aylık liderlik tablosundaki ilk 20 trader'ın hâlâ açık
          pozisyon tuttuğu ortak marketler; ROI'lerine göre ağırlıklandırılmış bir
          "akıllı para" skoruyla birlikte.
        </p>
        <div className="flex items-center gap-3 shrink-0">
          <button
            onClick={handleSync}
            disabled={syncing}
            className="text-sm font-medium rounded-lg px-4 py-2 bg-amber-500 text-ink-950 hover:bg-amber-400
                       disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            {syncing ? 'Senkronize ediliyor…' : 'Şimdi senkronize et'}
          </button>
          <Link
            to="/closed-bets"
            className="text-sm font-medium rounded-lg px-4 py-2 bg-ink-800 border border-ink-600 text-ash-100
                       hover:border-amber-600/50 transition-colors"
          >
            Son {CLOSED_WINDOW_DAYS} günde kapananları göster
          </Link>
        </div>
      </div>

      {loading && (
        <div className="text-ash-500 font-mono text-sm">Yükleniyor…</div>
      )}

      {error && (
        <div className="rounded-lg border border-signal-no/30 bg-signal-no/5 text-signal-no text-sm px-4 py-3 mb-6">
          Hata: {error}
        </div>
      )}

      {!loading && !error && markets.length === 0 && (
        <div className="rounded-2xl border border-dashed border-ink-600 text-ash-500 text-sm px-6 py-14 text-center">
          Bu kategori için henüz ortak bahis bulunamadı. Senkronizasyon henüz
          çalışmamış olabilir — yukarıdaki "Şimdi senkronize et" ile deneyebilirsin.
        </div>
      )}

      <div className="grid gap-4">
        {markets.map((m) => (
          <ConsensusMarketCard key={m.conditionId} market={m} />
        ))}
      </div>
    </>
  )
}
