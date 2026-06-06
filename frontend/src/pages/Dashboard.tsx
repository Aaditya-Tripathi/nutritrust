import { useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import {
  AlertCircle,
  ArrowRight,
  Beaker,
  ChartPie,
  Database,
  Droplets,
  Leaf,
  Loader2,
  PackageSearch,
  Search,
  ShieldAlert,
  Sparkles,
  TriangleAlert,
} from 'lucide-react'
import { fetchDashboard } from '../services/api'
import { formatDate, labelForMissing } from '../reportUtils'
import type { DashboardFlagDistributionItem, DashboardRecentReport } from '../types'

const distributionColors = ['#04735f', '#1d4ed8', '#b45309', '#b91c1c', '#64748b', '#7c3aed']

const filterOptions = [
  { label: 'All reports', value: 'all' },
  { label: 'Nutrition flags', value: 'nutrition' },
  { label: 'Allergen warnings', value: 'allergen' },
  { label: 'Ingredients or additives', value: 'ingredient-additive' },
  { label: 'Missing data', value: 'missing-data' },
] as const

type FilterValue = (typeof filterOptions)[number]['value']

export function Dashboard() {
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState<FilterValue>('all')
  const dashboardQuery = useQuery({
    queryKey: ['dashboard'],
    queryFn: fetchDashboard,
  })

  const filteredReports = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase()
    return (dashboardQuery.data?.recentReports ?? []).filter((report) => {
      const searchableText = [
        report.productName,
        report.barcode,
        report.brand,
        report.category,
        ...(report.tags ?? []),
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()
      const matchesQuery = !normalizedQuery || searchableText.includes(normalizedQuery)
      return matchesQuery && matchesFilter(report, filter)
    })
  }, [dashboardQuery.data?.recentReports, filter, query])

  const summary = dashboardQuery.data?.summary
  const totalDistribution = (dashboardQuery.data?.flagDistribution ?? []).reduce((sum, item) => sum + item.count, 0)

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <p className="eyebrow">Analytics Dashboard</p>
          <h2>Saved report intelligence</h2>
          <p>
            Review patterns across saved product reports without opening every barcode result individually.
          </p>
        </div>
      </header>

      {dashboardQuery.isLoading ? (
        <div className="state-card">
          <Loader2 size={24} className="spin" aria-hidden="true" />
          <div>
            <h3>Loading dashboard</h3>
            <p>Aggregating saved report facts from the backend history store.</p>
          </div>
        </div>
      ) : null}

      {dashboardQuery.error ? (
        <div className="state-card error">
          <AlertCircle size={24} aria-hidden="true" />
          <div>
            <h3>Unable to load dashboard</h3>
            <p>{dashboardQuery.error.message}</p>
          </div>
        </div>
      ) : null}

      {summary ? (
        <>
          <section className="dashboard-summary-grid" aria-label="Saved report summary">
            <SummaryCard
              label="Total Reports"
              value={summary.totalReports}
              detail="Saved barcode reports"
              tone="neutral"
              icon={<Database size={19} aria-hidden="true" />}
            />
            <SummaryCard
              label="High Sugar"
              value={summary.highSugarReports}
              detail="Products with HIGH sugar"
              tone={summary.highSugarReports > 0 ? 'danger' : 'success'}
              icon={<Droplets size={19} aria-hidden="true" />}
            />
            <SummaryCard
              label="High Salt"
              value={summary.highSaltReports}
              detail="Salt or sodium HIGH"
              tone={summary.highSaltReports > 0 ? 'danger' : 'success'}
              icon={<TriangleAlert size={19} aria-hidden="true" />}
            />
            <SummaryCard
              label="Saturated Fat"
              value={summary.saturatedFatReports}
              detail="Any generated sat-fat flag"
              tone={summary.saturatedFatReports > 0 ? 'warning' : 'success'}
              icon={<ChartPie size={19} aria-hidden="true" />}
            />
            <SummaryCard
              label="Allergen Warnings"
              value={summary.allergenWarningReports}
              detail="Flags or source gaps"
              tone={summary.allergenWarningReports > 0 ? 'danger' : 'success'}
              icon={<ShieldAlert size={19} aria-hidden="true" />}
            />
            <SummaryCard
              label="Missing Data"
              value={summary.missingDataReports}
              detail="Source quality warnings"
              tone={summary.missingDataReports > 0 ? 'warning' : 'success'}
              icon={<AlertCircle size={19} aria-hidden="true" />}
            />
            <SummaryCard
              label="Ingredients/Additives"
              value={summary.ingredientAdditiveReports}
              detail="Reports with related flags"
              tone={summary.ingredientAdditiveReports > 0 ? 'warning' : 'success'}
              icon={<Beaker size={19} aria-hidden="true" />}
            />
          </section>

          <section className="dashboard-analytics-grid">
            <div className="card-section dashboard-chart-card">
              <div className="section-heading">
                <div className="section-title">
                  <span className="section-icon success">
                    <ChartPie size={20} aria-hidden="true" />
                  </span>
                  <div>
                    <p className="eyebrow">Flag Distribution</p>
                    <h3>Factual check mix</h3>
                  </div>
                </div>
                <span className="count-chip">{totalDistribution} items</span>
              </div>
              <FlagPieChart items={dashboardQuery.data?.flagDistribution ?? []} />
            </div>

            <div className="card-section dashboard-table-card">
              <div className="section-heading dashboard-table-heading">
                <div className="section-title">
                  <span className="section-icon ai">
                    <PackageSearch size={20} aria-hidden="true" />
                  </span>
                  <div>
                    <p className="eyebrow">Recent Reports</p>
                    <h3>Filter saved products</h3>
                  </div>
                </div>
                <span className="count-chip">{filteredReports.length} shown</span>
              </div>

              <div className="dashboard-controls">
                <label className="dashboard-search">
                  <Search size={16} aria-hidden="true" />
                  <input
                    type="search"
                    value={query}
                    placeholder="Search product, barcode, brand, tag"
                    onChange={(event) => setQuery(event.target.value)}
                  />
                </label>
                <label className="dashboard-filter">
                  <span>Filter</span>
                  <select value={filter} onChange={(event) => setFilter(event.target.value as FilterValue)}>
                    {filterOptions.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </label>
              </div>

              <RecentReportsTable reports={filteredReports} />
            </div>
          </section>
        </>
      ) : null}
    </div>
  )
}

function SummaryCard({
  label,
  value,
  detail,
  tone,
  icon,
}: {
  label: string
  value: number
  detail: string
  tone: 'neutral' | 'success' | 'warning' | 'danger'
  icon: ReactNode
}) {
  return (
    <article className={`dashboard-summary-card ${tone}`}>
      <div className="dashboard-summary-icon">{icon}</div>
      <div>
        <p>{label}</p>
        <strong>{value}</strong>
        <span>{detail}</span>
      </div>
    </article>
  )
}

function FlagPieChart({ items }: { items: DashboardFlagDistributionItem[] }) {
  const nonZeroItems = items.filter((item) => item.count > 0)
  const total = nonZeroItems.reduce((sum, item) => sum + item.count, 0)

  if (total === 0) {
    return (
      <div className="dashboard-chart-empty">
        <Sparkles size={28} aria-hidden="true" />
        <strong>No saved flags yet</strong>
        <p>Generate product reports to populate the distribution chart.</p>
      </div>
    )
  }

  const segments = nonZeroItems.reduce<Array<DashboardFlagDistributionItem & {
    color: string
    dashArray: string
    dashOffset: number
    percent: number
  }>>((acc, item, index) => {
    const percent = item.count / total
    const dashOffset = -acc.reduce((sum, segment) => sum + segment.percent * 100, 0)
    const segment = {
      ...item,
      color: distributionColors[index % distributionColors.length],
      dashArray: `${percent * 100} ${100 - percent * 100}`,
      dashOffset,
      percent,
    }
    return [...acc, segment]
  }, [])

  return (
    <div className="dashboard-chart-layout">
      <svg className="dashboard-pie" viewBox="0 0 42 42" role="img" aria-label="Flag distribution pie chart">
        <circle className="dashboard-pie-track" cx="21" cy="21" r="15.9" />
        {segments.map((segment) => (
          <circle
            key={segment.category}
            className="dashboard-pie-segment"
            cx="21"
            cy="21"
            r="15.9"
            stroke={segment.color}
            strokeDasharray={segment.dashArray}
            strokeDashoffset={segment.dashOffset}
          />
        ))}
        <text x="21" y="19.5" textAnchor="middle">
          {total}
        </text>
        <text x="21" y="24.5" textAnchor="middle" className="dashboard-pie-subtext">
          facts
        </text>
      </svg>

      <div className="dashboard-legend">
        {segments.map((segment) => (
          <div key={segment.category}>
            <span style={{ background: segment.color }} />
            <strong>{segment.category}</strong>
            <em>
              {segment.count} / {Math.round(segment.percent * 100)}%
            </em>
          </div>
        ))}
      </div>
    </div>
  )
}

function RecentReportsTable({ reports }: { reports: DashboardRecentReport[] }) {
  if (reports.length === 0) {
    return (
      <div className="dashboard-chart-empty table-empty">
        <Leaf size={28} aria-hidden="true" />
        <strong>No matching reports</strong>
        <p>Adjust the search text or filter to review saved report rows.</p>
      </div>
    )
  }

  return (
    <div className="dashboard-table-wrap">
      <table className="dashboard-table">
        <thead>
          <tr>
            <th>Product</th>
            <th>Barcode</th>
            <th>Flags</th>
            <th>Warnings</th>
            <th>Created</th>
            <th aria-label="Open report" />
          </tr>
        </thead>
        <tbody>
          {reports.map((report) => (
            <tr key={report.id}>
              <td>
                <strong>{labelForMissing(report.productName)}</strong>
                <span>
                  {labelForMissing(report.brand)} / {labelForMissing(report.category)}
                </span>
                <div className="dashboard-tag-row">
                  {report.tags.slice(0, 4).map((tag) => (
                    <em key={tag}>{tag}</em>
                  ))}
                </div>
              </td>
              <td className="tabular-cell">{report.barcode}</td>
              <td>
                <MetricStack report={report} />
              </td>
              <td className={report.dataQualityWarningCount > 0 ? 'warning-cell' : ''}>
                {report.dataQualityWarningCount}
              </td>
              <td>{formatDate(report.createdAt ?? undefined)}</td>
              <td>
                <Link className="icon-button dashboard-open-button" to={`/reports/${report.id}`} aria-label={`Open report for barcode ${report.barcode}`}>
                  <ArrowRight size={17} aria-hidden="true" />
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function MetricStack({ report }: { report: DashboardRecentReport }) {
  const metrics = [
    { label: 'N', value: report.nutritionFlagCount },
    { label: 'I', value: report.ingredientFlagCount },
    { label: 'A+', value: report.additiveFlagCount },
    { label: 'Al', value: report.allergenFlagCount },
    { label: 'P', value: report.positiveSignalCount },
  ]

  return (
    <div className="dashboard-metric-stack" aria-label="Report fact counts">
      {metrics.map((metric) => (
        <span key={metric.label} title={metric.label}>
          {metric.label}:{metric.value}
        </span>
      ))}
    </div>
  )
}

function matchesFilter(report: DashboardRecentReport, filter: FilterValue) {
  if (filter === 'all') {
    return true
  }
  if (filter === 'nutrition') {
    return report.nutritionFlagCount > 0
  }
  if (filter === 'allergen') {
    return report.allergenFlagCount > 0 || report.tags.some((tag) => tag.toLowerCase().includes('allergen'))
  }
  if (filter === 'ingredient-additive') {
    return report.ingredientFlagCount > 0 || report.additiveFlagCount > 0
  }
  return report.dataQualityWarningCount > 0
}
