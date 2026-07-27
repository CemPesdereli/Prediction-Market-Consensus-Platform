import { Routes, Route } from 'react-router-dom'
import Layout from './Layout.jsx'
import ActiveBetsPage from './pages/ActiveBetsPage.jsx'
import ClosedBetsPage from './pages/ClosedBetsPage.jsx'
import WatchedBetsPage from './pages/WatchedBetsPage.jsx'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<ActiveBetsPage />} />
        <Route path="closed-bets" element={<ClosedBetsPage />} />
        <Route path="watched-bets" element={<WatchedBetsPage />} />
      </Route>
    </Routes>
  )
}
