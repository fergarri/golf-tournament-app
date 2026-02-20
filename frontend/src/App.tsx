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
import AdministrationPage from './pages/AdministrationPage'
import TournamentAdminDetailPage from './pages/TournamentAdminDetailPage'
import ProtectedRoute from './components/ProtectedRoute'
import PermissionRoute from './components/PermissionRoute'
import Layout from './components/Layout'

function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        
        {/* Protected routes MUST come before public routes to avoid conflicts */}
        <Route element={<ProtectedRoute><Layout /></ProtectedRoute>}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/tournaments" element={<PermissionRoute permission="GAMES"><TournamentsPage /></PermissionRoute>} />
          <Route path="/tournaments/:id/leaderboard" element={<PermissionRoute permission="GAMES"><TournamentLeaderboardPage /></PermissionRoute>} />
          <Route path="/players" element={<PermissionRoute permission="GAMES"><PlayersPage /></PermissionRoute>} />
          <Route path="/courses" element={<PermissionRoute permission="GAMES"><CoursesPage /></PermissionRoute>} />
          <Route path="/users" element={<PermissionRoute permission="TOTAL"><UsersPage /></PermissionRoute>} />
          <Route path="/administration" element={<PermissionRoute permission="ADMINISTRATION"><AdministrationPage /></PermissionRoute>} />
          <Route path="/administration/:id" element={<PermissionRoute permission="ADMINISTRATION"><TournamentAdminDetailPage /></PermissionRoute>} />
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
