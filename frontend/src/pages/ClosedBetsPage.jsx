import { useEffect, useState, useCallback } from 'react'
import { useOutletContext, Link } from 'react-router-dom'
import { fetchClosedBets } from '../api.js'
import ClosedBetsPanel from '../components/ClosedBetsPanel.jsx'
import { CLOSED_WINDOW_DAYS } from '../constants.js'

export default function ClosedBetsPage() {
  const { category } = useOutletContext()
  const [markets, setMarkets] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const load = useCallback((cat) => {
    setLoading(true)
    setError(null)
    fetchClosedBets(cat)
      .then(setMarkets)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    load(category)
  }, [category, load])

  return (
    <>
      <Link
        to="/"
        className="inline-flex items-center gap-1.5 text-sm text-ash-300 hover:text-amber-400 transition-colors mb-6"
      >
        <span aria-hidden="true">←</span> Geri
      </Link>

      <ClosedBetsPanel
        markets={markets}
        loading={loading}
        error={error}
        windowDays={CLOSED_WINDOW_DAYS}
      />
    </>
  )
}
