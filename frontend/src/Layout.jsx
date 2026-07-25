import { useEffect, useState } from 'react'
import { Outlet } from 'react-router-dom'
import { fetchCategories } from './api.js'

export default function Layout() {
  const [categories, setCategories] = useState([])
  const [category, setCategory] = useState('POLITICS')

  useEffect(() => {
    fetchCategories().then(setCategories).catch(() => setCategories([
      'OVERALL', 'POLITICS', 'SPORTS', 'ESPORTS', 'CRYPTO', 'CULTURE',
      'MENTIONS', 'WEATHER', 'ECONOMICS', 'TECH', 'FINANCE',
    ]))
  }, [])

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
        </div>
      </header>

      <main className="max-w-5xl mx-auto px-6 py-8">
        <Outlet context={{ category }} />
      </main>
    </div>
  )
}
