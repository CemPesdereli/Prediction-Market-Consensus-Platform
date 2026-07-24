import { useEffect, useState, useCallback } from 'react'
import { fetchCategories, fetchCommonBets, triggerSync } from './api.js'
import ConsensusMarketCard from './components/ConsensusMarketCard.jsx'

export default function App() {
  const [categories, setCategories] = useState([])
  const [category, setCategory] = useState('POLITICS')
  const [markets, setMarkets] = useState([])
  const [loading, setLoading] = useState(true)
  const [syncing, setSyncing] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    fetchCategories().then(setCategories).catch(() => setCategories([
      'OVERALL', 'POLITICS', 'SPORTS', 'ESPORTS', 'CRYPTO', 'CULTURE',
      'MENTIONS', 'WEATHER', 'ECONOMICS', 'TECH', 'FINANCE',
    ]))
  }, [])

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
    <div className="min-h-screen">
      <header className="border-b border-ink-700">
        <div className="max-w-5xl mx-auto px-6 py-6 flex items-center justify-between gap-4 flex-wrap">
          <div>
            <p className="text-[11px] uppercase tracking-[0.2em] text-amber-500 font-mono mb-1">
              Prediction Market Intelligence
            </p>
            <h1 className="font-display text-2xl font-bold text-ash-100">
              Consensus <span className="text-ash-500 font-normal">/ ROI-weighted signal</span>
            </h1>
          </div>

          <div className="flex items-center gap-3">
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              className="bg-ink-800 border border-ink-600 text-ash-100 text-sm rounded-lg px-3 py-2 font-mono
                         focus:outline-none focus:ring-2 focus:ring-amber-500/60"
            >
              {categories.map((c) => (
                <option key={c} value={c}>{c}</option>
              ))}
            </select>
            <button
              onClick={handleSync}
              disabled={syncing}
              className="text-sm font-medium rounded-lg px-4 py-2 bg-amber-500 text-ink-950 hover:bg-amber-400
                         disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {syncing ? 'Senkronize ediliyor…' : 'Şimdi senkronize et'}
            </button>
          </div>
        </div>
      </header>

      <main className="max-w-5xl mx-auto px-6 py-8">
        <p className="text-sm text-ash-500 mb-6 max-w-2xl">
          Seçilen kategorinin aylık liderlik tablosundaki ilk 20 trader'ın hâlâ açık
          pozisyon tuttuğu ortak marketler; ROI'lerine göre ağırlıklandırılmış bir
          "akıllı para" skoruyla birlikte.
        </p>

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
      </main>
    </div>
  )
}
