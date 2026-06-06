import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { AlertCircle, ArrowRight, Loader2, PackageSearch, Trash2 } from 'lucide-react'
import { ApiError, deleteSavedReport, fetchReportHistory } from '../services/api'
import { formatDate, labelForMissing } from '../reportUtils'

export function SavedReports() {
  const queryClient = useQueryClient()
  const historyQuery = useQuery({
    queryKey: ['reports'],
    queryFn: fetchReportHistory,
  })
  const deleteMutation = useMutation<void, ApiError, number>({
    mutationFn: deleteSavedReport,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['reports'] }),
  })

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <p className="eyebrow">Saved Reports</p>
          <h2>Review history</h2>
          <p>Newest saved product reports from the backend history store.</p>
        </div>
      </header>

      {historyQuery.isLoading ? (
        <div className="state-card">
          <Loader2 size={24} className="spin" aria-hidden="true" />
          <div>
            <h3>Loading reports</h3>
            <p>Reading saved report history.</p>
          </div>
        </div>
      ) : null}

      {historyQuery.error ? (
        <div className="state-card error">
          <AlertCircle size={24} aria-hidden="true" />
          <div>
            <h3>Unable to load saved reports</h3>
            <p>{historyQuery.error.message}</p>
          </div>
        </div>
      ) : null}

      {historyQuery.data?.length === 0 ? (
        <section className="empty-dashboard">
          <div>
            <p className="eyebrow">No saved reports</p>
            <h3>Analyze a product to create history</h3>
            <p>Reports are saved by the backend when a product report is generated.</p>
          </div>
        </section>
      ) : null}

      {historyQuery.data && historyQuery.data.length > 0 ? (
        <section className="history-list">
          {historyQuery.data.map((report) => (
            <article className="history-row" key={report.id}>
              <div className="history-icon">
                <PackageSearch size={20} aria-hidden="true" />
              </div>
              <div className="history-main">
                <h3>{labelForMissing(report.productName)}</h3>
                <div className="metadata-row">
                  <span>{report.barcode}</span>
                  <span>{labelForMissing(report.brand)}</span>
                  <span>{labelForMissing(report.category)}</span>
                  <span>{formatDate(report.createdAt)}</span>
                </div>
              </div>
              <div className="history-actions">
                <button
                  type="button"
                  className="icon-button danger"
                  onClick={() => deleteMutation.mutate(report.id)}
                  disabled={deleteMutation.isPending}
                  aria-label={`Delete report for barcode ${report.barcode}`}
                >
                  <Trash2 size={17} aria-hidden="true" />
                </button>
                <Link className="secondary-button" to={`/reports/${report.id}`}>
                  Open report
                  <ArrowRight size={16} aria-hidden="true" />
                </Link>
              </div>
            </article>
          ))}
        </section>
      ) : null}
    </div>
  )
}
