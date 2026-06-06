import type {
  DashboardResponse,
  ErrorResponse,
  ProductReportRequest,
  ProductReportResponse,
  ReportHistoryItem,
  SavedProductReportResponse,
} from '../types'

const NETWORK_ERROR_MESSAGE =
  'Could not connect to backend. Make sure Spring Boot is running on port 8080.'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL?.trim() || ''

export class ApiError extends Error {
  status: number
  body: unknown

  constructor(status: number, message: string, body: unknown) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}

async function readBody(response: Response) {
  const text = await response.text()
  if (!text) {
    return null
  }

  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response

  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      headers: {
        'Content-Type': 'application/json',
        ...init?.headers,
      },
      ...init,
    })
  } catch (error) {
    throw new ApiError(0, NETWORK_ERROR_MESSAGE, error)
  }

  const body = await readBody(response)

  if (!response.ok) {
    const message =
      typeof body === 'object' && body && 'message' in body
        ? String((body as ErrorResponse).message)
        : `Request failed with status ${response.status}`
    throw new ApiError(response.status, message, body)
  }

  return body as T
}

export async function fetchReportByBarcode(barcode: string) {
  return request<ProductReportResponse>(`/api/products/report/${barcode}`)
}

export async function createReportWithManualLabel(payload: ProductReportRequest) {
  return request<ProductReportResponse>('/api/products/report', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function fetchDashboard() {
  return request<DashboardResponse>('/api/dashboard')
}

export async function fetchReportHistory() {
  return request<ReportHistoryItem[]>('/api/reports')
}

export async function fetchSavedReport(id: string | number) {
  return request<SavedProductReportResponse>(`/api/reports/${id}`)
}

export async function deleteSavedReport(id: string | number) {
  await request<null>(`/api/reports/${id}`, {
    method: 'DELETE',
  })
}
