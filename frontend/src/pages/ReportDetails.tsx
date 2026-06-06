import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { AlertCircle, ArrowLeft, Loader2, Trash2 } from 'lucide-react'
import { ApiError, deleteSavedReport, fetchSavedReport } from '../services/api'
import { ReportView } from '../components/ReportView'
import { normalizeSavedReport } from '../reportUtils'

export function ReportDetails() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const savedReportQuery = useQuery({
    queryKey: ['reports', id],
    queryFn: () => fetchSavedReport(id ?? ''),
    enabled: Boolean(id),
  })

  const deleteMutation = useMutation<void, ApiError, string>({
    mutationFn: (reportId) => deleteSavedReport(reportId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['reports'] })
      navigate('/reports')
    },
  })

  return (
    <div className="page">
      <header className="page-header split">
        <div>
          <p className="eyebrow">Report Details</p>
          <h2>Saved product report</h2>
          <p>Full saved report with factual flags, data warnings, and reviewer narrative.</p>
        </div>
        <Link className="secondary-button" to="/reports">
          <ArrowLeft size={16} aria-hidden="true" />
          Back
        </Link>
      </header>

      {savedReportQuery.isLoading ? (
        <div className="state-card">
          <Loader2 size={24} className="spin" aria-hidden="true" />
          <div>
            <h3>Loading report</h3>
            <p>Opening saved report details.</p>
          </div>
        </div>
      ) : null}

      {savedReportQuery.error ? (
        <div className="state-card error">
          <AlertCircle size={24} aria-hidden="true" />
          <div>
            <h3>Unable to open report</h3>
            <p>{savedReportQuery.error.message}</p>
          </div>
        </div>
      ) : null}

      {savedReportQuery.data ? (
        <ReportView
          report={normalizeSavedReport(savedReportQuery.data)}
          actions={
            <button
              type="button"
              className="secondary-button danger"
              onClick={() => id && deleteMutation.mutate(id)}
              disabled={deleteMutation.isPending}
            >
              {deleteMutation.isPending ? (
                <Loader2 size={16} className="spin" aria-hidden="true" />
              ) : (
                <Trash2 size={16} aria-hidden="true" />
              )}
              Delete
            </button>
          }
        />
      ) : null}
    </div>
  )
}
