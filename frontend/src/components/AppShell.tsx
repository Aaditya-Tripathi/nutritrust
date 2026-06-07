import {
  Archive,
  ClipboardList,
  Gauge,
  History,
  KeyRound,
  LayoutDashboard,
  ShieldCheck,
} from 'lucide-react'
import { AnimatePresence, motion } from 'motion/react'
import { NavLink, useLocation, useOutlet } from 'react-router-dom'
import { enter, exit, routeInitial, springSoft } from '../motion'

const navItems = [
  { to: '/dashboard', label: 'Dashboard', icon: Gauge },
  { to: '/', label: 'Analyze Product', icon: LayoutDashboard },
  { to: '/reports', label: 'Saved Reports', icon: History },
  { to: '/groq-api-key', label: 'Groq API Key', icon: KeyRound },
]

export function AppShell() {
  const location = useLocation()
  const outlet = useOutlet()

  return (
    <div className="app-shell">
      <aside className="sidebar" aria-label="Primary navigation">
        <div className="brand-lockup">
          <div className="brand-mark">
            <ShieldCheck size={22} aria-hidden="true" />
          </div>
          <div>
            <p className="eyebrow">NutriTrust AI</p>
            <h1>Review Console</h1>
          </div>
        </div>

        <nav className="sidebar-nav">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}
            >
              <item.icon size={18} aria-hidden="true" />
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-panel">
          <div className="panel-icon">
            <ClipboardList size={18} aria-hidden="true" />
          </div>
          <p className="sidebar-panel-title">Internal workflow</p>
          <p>
            Factual rule checks from product data, with AI used only for neutral reviewer text.
          </p>
        </div>

        <div className="sidebar-metrics" aria-label="Console guardrails">
          <div>
            <Gauge size={16} aria-hidden="true" />
            <span>No product scoring</span>
          </div>
          <div>
            <Archive size={16} aria-hidden="true" />
            <span>Saved report history</span>
          </div>
        </div>
      </aside>

      <main className="main-surface">
        <AnimatePresence initial={false} mode="wait">
          <motion.div
            key={location.pathname}
            className="route-frame"
            initial={routeInitial}
            animate={enter}
            exit={exit}
            transition={springSoft}
          >
            {outlet}
          </motion.div>
        </AnimatePresence>
      </main>
    </div>
  )
}
