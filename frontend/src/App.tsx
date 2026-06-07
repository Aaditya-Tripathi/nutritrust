import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import { AppShell } from './components/AppShell'
import { AnalyzeProduct } from './pages/AnalyzeProduct'
import { Dashboard } from './pages/Dashboard'
import { GroqApiKey } from './pages/GroqApiKey'
import { NotFound } from './pages/NotFound'
import { ReportDetails } from './pages/ReportDetails'
import { SavedReports } from './pages/SavedReports'

const router = createBrowserRouter([
  {
    path: '/',
    element: <AppShell />,
    children: [
      { index: true, element: <AnalyzeProduct /> },
      { path: 'dashboard', element: <Dashboard /> },
      { path: 'reports', element: <SavedReports /> },
      { path: 'reports/:id', element: <ReportDetails /> },
      { path: 'groq-api-key', element: <GroqApiKey /> },
      { path: '*', element: <NotFound /> },
    ],
  },
])

export default function App() {
  return <RouterProvider router={router} />
}
