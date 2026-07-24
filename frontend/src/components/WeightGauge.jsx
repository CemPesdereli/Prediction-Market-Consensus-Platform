export default function WeightGauge({ weightedPercent, plainPercent }) {
  const w = Math.max(0, Math.min(100, weightedPercent))
  const p = Math.max(0, Math.min(100, plainPercent))

  return (
    <div className="w-full">
      <div className="relative h-2.5 rounded-full bg-ink-700 overflow-visible">
        <div
          className="h-full rounded-full bg-gradient-to-r from-amber-600 to-amber-400 transition-all duration-500"
          style={{ width: `${w}%` }}
        />
        {/* Ham (agirliksiz) headcount yuzdesini gosteren ince isaretci */}
        <div
          className="absolute top-1/2 -translate-y-1/2 w-[3px] h-4 bg-ash-100/80 rounded-full shadow-[0_0_4px_rgba(0,0,0,0.6)]"
          style={{ left: `calc(${p}% - 1.5px)` }}
          title={`Ham katılım: %${p.toFixed(1)}`}
        />
      </div>
      <div className="flex justify-between mt-1.5 text-[10px] uppercase tracking-wider text-ash-500">
        <span>ROI-ağırlıklı skor</span>
        <span className="flex items-center gap-1">
          <span className="inline-block w-1.5 h-1.5 rounded-full bg-ash-100/80" />
          ham katılım işareti
        </span>
      </div>
    </div>
  )
}
