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

export async function triggerSync(category) {
  const res = await fetch(`${BASE}/sync?category=${category}`, { method: 'POST' })
  if (!res.ok) throw new Error('Senkronizasyon tetiklenemedi')
  return res.text()
}
