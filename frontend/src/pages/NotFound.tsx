import { Link } from 'react-router-dom'
import { ArrowLeft, SearchX } from 'lucide-react'

export function NotFound() {
  return (
    <div className="page">
      <section className="empty-dashboard">
        <SearchX size={28} aria-hidden="true" />
        <div>
          <p className="eyebrow">Page not found</p>
          <h3>This workspace view does not exist</h3>
          <p>Return to product analysis to continue reviewing packaged food reports.</p>
        </div>
        <Link className="secondary-button" to="/">
          <ArrowLeft size={16} aria-hidden="true" />
          Analyze Product
        </Link>
      </section>
    </div>
  )
}
