const BASE = '/api'

export async function fetchCategories() {
  const res = await fetch(`${BASE}/categories`)
  if (!res.ok) throw new Error('Kategoriler alınamadı')
  return res.json()
}

export async function fetchCommonBets(category) {
  const res = await fetch(`${BASE}/common-bets?category=${category}`)
  if (!res.ok) throw new Error('Consensus verisi alınamadı')
  return res.json()
}

export async function fetchClosedBets(category) {
  const res = await fetch(`${BASE}/closed-bets?category=${category}`)
  if (!res.ok) throw new Error('Kapanmış bahis verisi alınamadı')
  return res.json()
}

export async function triggerSync(category) {
  const res = await fetch(`${BASE}/sync?category=${category}`, { method: 'POST' })
  if (!res.ok) throw new Error('Senkronizasyon tetiklenemedi')
  return res.text()
}

export async function fetchWatchedBets() {
  const res = await fetch(`${BASE}/watched-bets`)
  if (!res.ok) throw new Error('Alarmlar alınamadı')
  return res.json()
}

export async function createWatchedBet({ marketSlug, outcome, entryPrice, targetPrice }) {
  const res = await fetch(`${BASE}/watched-bets`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ marketSlug, outcome, entryPrice, targetPrice }),
  })
  if (!res.ok) {
    const message = await res.text().catch(() => null)
    throw new Error(message || 'Alarm oluşturulamadı')
  }
  return res.json()
}

export async function cancelWatchedBet(id) {
  const res = await fetch(`${BASE}/watched-bets/${id}`, { method: 'DELETE' })
  if (!res.ok) throw new Error('Alarm iptal edilemedi')
  return res.json()
}
