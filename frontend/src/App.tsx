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
import FrutalesLeaderboardPage from './pages/FrutalesLeaderboardPage'
import PublicFrutalesLeaderboardPage from './pages/PublicFrutalesLeaderboardPage'
import ScorecardPage from './pages/ScorecardPage'
import LeaderboardPage from './pages/LeaderboardPage'
import AdministrationPage from './pages/AdministrationPage'
import TournamentAdminDetailPage from './pages/TournamentAdminDetailPage'
import TournamentAdminStagesPage from './pages/TournamentAdminStagesPage'
import TournamentAdminStageBoardPage from './pages/TournamentAdminStageBoardPage'
import TournamentAdminPlayoffResultsPage from './pages/TournamentAdminPlayoffResultsPage'
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
          <Route path="/tournaments/:id/frutales-leaderboard" element={<PermissionRoute permission="GAMES"><FrutalesLeaderboardPage /></PermissionRoute>} />
          <Route path="/players" element={<PermissionRoute permission="GAMES"><PlayersPage /></PermissionRoute>} />
          <Route path="/courses" element={<PermissionRoute permission="GAMES"><CoursesPage /></PermissionRoute>} />
          <Route path="/users" element={<PermissionRoute permission="TOTAL"><UsersPage /></PermissionRoute>} />
          <Route path="/administration" element={<PermissionRoute permission="ADMINISTRATION"><AdministrationPage /></PermissionRoute>} />
          <Route path="/administration/:id" element={<PermissionRoute permission="ADMINISTRATION"><TournamentAdminDetailPage /></PermissionRoute>} />
          <Route path="/administration/:id/stages" element={<PermissionRoute permission="ADMINISTRATION"><TournamentAdminStagesPage /></PermissionRoute>} />
          <Route path="/administration/:id/stages/:stageId" element={<PermissionRoute permission="ADMINISTRATION"><TournamentAdminStageBoardPage /></PermissionRoute>} />
          <Route path="/administration/:id/stages/playoff-results" element={<PermissionRoute permission="ADMINISTRATION"><TournamentAdminPlayoffResultsPage /></PermissionRoute>} />
        </Route>

        {/* Public routes come after protected routes */}
        <Route path="/inscribe/:codigo" element={<PublicInscriptionPage />} />
        <Route path="/play/:codigo" element={<TournamentAccessPage />} />
        <Route path="/play/:codigo/scorecard" element={<TournamentScorecardPage />} />
        <Route path="/tournaments/:codigo/scorecard" element={<ScorecardPage />} />
        <Route path="/tournaments/:codigo/leaderboard" element={<LeaderboardPage />} />
        <Route path="/results/:codigo" element={<PublicLeaderboardPage />} />
        <Route path="/frutales-results/:codigo" element={<PublicFrutalesLeaderboardPage />} />
      </Routes>
    </AuthProvider>
  )
}

export default App
