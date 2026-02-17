import { Routes, Route } from 'react-router-dom'
import { AuthProvider } from './hooks/useAuth'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import TournamentsPage from './pages/TournamentsPage'
import PlayersPage from './pages/PlayersPage'
import CoursesPage from './pages/CoursesPage'
import UsersPage from './pages/UsersPage'
import TournamentLeaderboardPage from './pages/TournamentLeaderboardPage'
import TournamentAccessPage from './pages/TournamentAccessPage'
import TournamentScorecardPage from './pages/TournamentScorecardPage'
import PublicInscriptionPage from './pages/PublicInscriptionPage'
import PublicLeaderboardPage from './pages/PublicLeaderboardPage'
import ScorecardPage from './pages/ScorecardPage'
import LeaderboardPage from './pages/LeaderboardPage'
import ProtectedRoute from './components/ProtectedRoute'
import Layout from './components/Layout'

function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        
        {/* Protected routes MUST come before public routes to avoid conflicts */}
        <Route element={<ProtectedRoute><Layout /></ProtectedRoute>}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/tournaments" element={<TournamentsPage />} />
          <Route path="/tournaments/:id/leaderboard" element={<TournamentLeaderboardPage />} />
          <Route path="/players" element={<PlayersPage />} />
          <Route path="/courses" element={<CoursesPage />} />
          <Route path="/users" element={<UsersPage />} />
        </Route>

        {/* Public routes come after protected routes */}
        <Route path="/inscribe/:codigo" element={<PublicInscriptionPage />} />
        <Route path="/play/:codigo" element={<TournamentAccessPage />} />
        <Route path="/play/:codigo/scorecard" element={<TournamentScorecardPage />} />
        <Route path="/tournaments/:codigo/scorecard" element={<ScorecardPage />} />
        <Route path="/tournaments/:codigo/leaderboard" element={<LeaderboardPage />} />
        <Route path="/results/:codigo" element={<PublicLeaderboardPage />} />
      </Routes>
    </AuthProvider>
  )
}

export default App
