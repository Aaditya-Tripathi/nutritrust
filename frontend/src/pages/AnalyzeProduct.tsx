import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { useMutation } from '@tanstack/react-query'
import type { IScannerControls } from '@zxing/browser'
import { AnimatePresence, motion } from 'motion/react'
import {
  AlertCircle,
  ArrowRight,
  Barcode,
  Camera,
  CheckCircle2,
  ClipboardPlus,
  Loader2,
  ScanLine,
  Search,
  VideoOff,
  X,
} from 'lucide-react'
import { ApiError, createReportWithManualLabel, fetchReportByBarcode } from '../services/api'
import { ReportView } from '../components/ReportView'
import { readStoredGroqApiKey } from '../groqKeyStorage'
import {
  enter,
  exit,
  quickExit,
  routeInitial,
  scaleFadeEnter,
  scaleFadeExit,
  scaleFadeInitial,
  springSoft,
} from '../motion'
import { normalizeLiveReport } from '../reportUtils'
import type { ProductReportRequest, ProductReportResponse } from '../types'

type FormState = ProductReportRequest

const initialForm: FormState = {
  barcode: '',
  manualIngredientsText: '',
  manualAllergenText: '',
  manualNutritionNote: '',
}

const CAMERA_PERMISSION_MESSAGE =
  'Camera access was blocked. You can still enter the barcode manually.'
const CAMERA_SECURE_CONTEXT_MESSAGE =
  'Camera scanning needs a secure HTTPS connection on phones. You can still enter the barcode manually.'
const BARCODE_CAMERA_CONSTRAINTS: MediaStreamConstraints = {
  audio: false,
  video: {
    facingMode: { ideal: 'environment' },
    width: { ideal: 1280 },
    height: { ideal: 720 },
  },
}

function isLocalCameraHost() {
  const hostname = window.location.hostname
  return hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '::1'
}

export function AnalyzeProduct() {
  const [form, setForm] = useState<FormState>(initialForm)
  const [manualOpen, setManualOpen] = useState(false)
  const [scannerOpen, setScannerOpen] = useState(false)
  const [detectedBarcode, setDetectedBarcode] = useState('')
  const [scannerStream, setScannerStream] = useState<MediaStream | null>(null)
  const [scannerError, setScannerError] = useState<string | null>(null)
  const scannerStreamRef = useRef<MediaStream | null>(null)
  const scannerRequestIdRef = useRef(0)

  const reportMutation = useMutation<ProductReportResponse, ApiError, ProductReportRequest>({
    mutationFn: (payload) => {
      const hasManualText = Boolean(
        payload.manualIngredientsText?.trim() ||
          payload.manualAllergenText?.trim() ||
          payload.manualNutritionNote?.trim(),
      )
      const hasGroqApiKey = Boolean(payload.groqApiKey?.trim())

      if (hasManualText || hasGroqApiKey) {
        return createReportWithManualLabel(payload)
      }

      return fetchReportByBarcode(payload.barcode)
    },
  })

  function updateField(field: keyof FormState, value: string) {
    setForm((current) => ({ ...current, [field]: value }))
  }

  const stopScannerStream = useCallback(() => {
    scannerStreamRef.current?.getTracks().forEach((track) => track.stop())
    scannerStreamRef.current = null
    setScannerStream(null)
  }, [])

  const handleBarcodeDetected = useCallback((barcode: string) => {
    setDetectedBarcode(barcode)
    updateField('barcode', barcode)
    stopScannerStream()
  }, [stopScannerStream])

  useEffect(() => {
    return () => {
      scannerRequestIdRef.current += 1
      scannerStreamRef.current?.getTracks().forEach((track) => track.stop())
      scannerStreamRef.current = null
    }
  }, [])

  const analyzeRequest = useCallback(
    (barcode: string) => {
      const normalizedBarcode = barcode.trim()
      if (!normalizedBarcode) {
        return
      }
      const savedGroqApiKey = readStoredGroqApiKey().trim()

      reportMutation.mutate({
        barcode: normalizedBarcode,
        manualIngredientsText: form.manualIngredientsText?.trim() || undefined,
        manualAllergenText: form.manualAllergenText?.trim() || undefined,
        manualNutritionNote: form.manualNutritionNote?.trim() || undefined,
        groqApiKey: savedGroqApiKey || undefined,
      })
    },
    [
      form.manualAllergenText,
      form.manualIngredientsText,
      form.manualNutritionNote,
      reportMutation,
    ],
  )

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    analyzeRequest(form.barcode)
  }

  async function openScanner() {
    const requestId = scannerRequestIdRef.current + 1
    scannerRequestIdRef.current = requestId
    stopScannerStream()
    setDetectedBarcode('')
    setScannerError(null)
    setScannerOpen(true)

    if (!window.isSecureContext && !isLocalCameraHost()) {
      setScannerError(CAMERA_SECURE_CONTEXT_MESSAGE)
      return
    }

    if (!navigator.mediaDevices?.getUserMedia) {
      setScannerError(CAMERA_PERMISSION_MESSAGE)
      return
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia(BARCODE_CAMERA_CONSTRAINTS)
      if (scannerRequestIdRef.current !== requestId) {
        stream.getTracks().forEach((track) => track.stop())
        return
      }

      scannerStreamRef.current = stream
      setScannerStream(stream)
    } catch {
      if (scannerRequestIdRef.current === requestId) {
        setScannerError(CAMERA_PERMISSION_MESSAGE)
      }
    }
  }

  const closeScanner = useCallback(() => {
    scannerRequestIdRef.current += 1
    stopScannerStream()
    setScannerError(null)
    setScannerOpen(false)
  }, [stopScannerStream])

  const analyzeDetectedBarcode = useCallback(
    (barcode: string) => {
      setScannerOpen(false)
      analyzeRequest(barcode)
    },
    [analyzeRequest],
  )

  const isNotFound =
    reportMutation.error?.status === 404 &&
    typeof reportMutation.error.body === 'object' &&
    reportMutation.error.body &&
    'found' in reportMutation.error.body
  let resultPanel: ReactNode = null
  let resultPanelKey = 'idle'

  if (reportMutation.isIdle) {
    resultPanel = <EmptyStartState />
  } else if (reportMutation.isPending) {
    resultPanelKey = 'pending'
    resultPanel = (
      <div className="state-card">
        <Loader2 size={24} className="spin" aria-hidden="true" />
        <div>
          <h3>Generating report</h3>
          <p>Fetching source data and running factual checks.</p>
        </div>
      </div>
    )
  } else if (reportMutation.error) {
    resultPanelKey = isNotFound ? 'not-found' : 'error'
    resultPanel = isNotFound ? (
      <div className="state-card not-found">
        <AlertCircle size={24} aria-hidden="true" />
        <div>
          <h3>Product not found</h3>
          <p>
            No Open Food Facts record was found for this barcode. Try another barcode or
            verify the digits on the package.
          </p>
        </div>
      </div>
    ) : (
      <div className="state-card error">
        <AlertCircle size={24} aria-hidden="true" />
        <div>
          <h3>Unable to load report</h3>
          <p>{reportMutation.error.message}</p>
        </div>
      </div>
    )
  } else if (reportMutation.data) {
    resultPanelKey = `report-${reportMutation.data.barcode}`
    resultPanel = <ReportView report={normalizeLiveReport(reportMutation.data)} />
  }

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <p className="eyebrow">Analyze Product</p>
          <h2>Food quality intelligence for grocery review teams</h2>
          <p>
            Search by packaged food barcode, add optional label text when source data is
            incomplete, and review factual flags in one workspace.
          </p>
        </div>
      </header>

      <section className="lookup-card">
        <div className="lookup-card-copy">
          <div className="lookup-icon">
            <Barcode size={22} aria-hidden="true" />
          </div>
          <div>
            <h3>Barcode lookup</h3>
            <p>Open Food Facts data is checked by backend rules before the report is saved.</p>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="lookup-form">
          <label htmlFor="barcode">Barcode</label>
          <div className="barcode-input-row">
            <input
              id="barcode"
              inputMode="numeric"
              pattern="[0-9]*"
              minLength={6}
              maxLength={18}
              placeholder="8901088068758"
              value={form.barcode}
              onChange={(event) => updateField('barcode', event.target.value)}
            />
            <button type="button" className="scanner-button" onClick={openScanner}>
              <Camera size={17} aria-hidden="true" />
              Scan Barcode
            </button>
            <button className="primary-button" disabled={reportMutation.isPending || !form.barcode.trim()}>
              {reportMutation.isPending ? (
                <Loader2 size={17} className="spin" aria-hidden="true" />
              ) : (
                <Search size={17} aria-hidden="true" />
              )}
              Analyze
            </button>
          </div>

          <button
            type="button"
            className="text-button"
            onClick={() => setManualOpen((open) => !open)}
            aria-expanded={manualOpen}
          >
            <ClipboardPlus size={17} aria-hidden="true" />
            Manual label override
            <ArrowRight size={16} className={manualOpen ? 'rotate' : ''} aria-hidden="true" />
          </button>

          <AnimatePresence initial={false}>
            {manualOpen ? (
              <motion.div
                className="manual-grid"
                initial={{ height: 0, opacity: 0, filter: 'blur(4px)' }}
                animate={{ height: 'auto', opacity: 1, filter: 'blur(0px)' }}
                exit={{ height: 0, opacity: 0, filter: 'blur(4px)', transition: quickExit }}
                transition={springSoft}
              >
                <label>
                  Ingredients text
                  <textarea
                    value={form.manualIngredientsText}
                    onChange={(event) => updateField('manualIngredientsText', event.target.value)}
                    placeholder="Paste ingredient text from the package label"
                  />
                </label>
                <label>
                  Allergen or trace text
                  <textarea
                    value={form.manualAllergenText}
                    onChange={(event) => updateField('manualAllergenText', event.target.value)}
                    placeholder="Paste allergen and trace declarations"
                  />
                </label>
                <label className="manual-note">
                  Nutrition note
                  <textarea
                    value={form.manualNutritionNote}
                    onChange={(event) => updateField('manualNutritionNote', event.target.value)}
                    placeholder="Optional reviewer note about visible nutrition label data"
                  />
                </label>
              </motion.div>
            ) : null}
          </AnimatePresence>
        </form>
      </section>

      <AnimatePresence initial={false}>
        {scannerOpen ? (
          <BarcodeScannerModal
            key="barcode-scanner"
            detectedBarcode={detectedBarcode}
            scannerError={scannerError}
            scannerStream={scannerStream}
            onBarcodeDetected={handleBarcodeDetected}
            onAnalyze={analyzeDetectedBarcode}
            onScannerError={setScannerError}
            onClose={closeScanner}
          />
        ) : null}
      </AnimatePresence>

      <AnimatePresence initial={false} mode="wait">
        {resultPanel ? (
          <motion.div
            key={resultPanelKey}
            className="result-motion-frame"
            initial={routeInitial}
            animate={enter}
            exit={exit}
            transition={springSoft}
          >
            {resultPanel}
          </motion.div>
        ) : null}
      </AnimatePresence>
    </div>
  )
}

type BarcodeScannerModalProps = {
  detectedBarcode: string
  scannerError: string | null
  scannerStream: MediaStream | null
  onBarcodeDetected: (barcode: string) => void
  onAnalyze: (barcode: string) => void
  onScannerError: (message: string) => void
  onClose: () => void
}

function BarcodeScannerModal({
  detectedBarcode,
  scannerError,
  scannerStream,
  onBarcodeDetected,
  onAnalyze,
  onScannerError,
  onClose,
}: BarcodeScannerModalProps) {
  const videoRef = useRef<HTMLVideoElement | null>(null)
  const controlsRef = useRef<IScannerControls | null>(null)
  const detectedRef = useRef(false)

  const stopScanner = useCallback(() => {
    controlsRef.current?.stop()
    controlsRef.current = null
  }, [])

  useEffect(() => {
    let isMounted = true
    detectedRef.current = false

    async function startScanner() {
      if (!videoRef.current || !scannerStream || detectedBarcode) {
        return
      }

      try {
        const { BarcodeFormat, BrowserMultiFormatReader } = await import('@zxing/browser')
        if (!isMounted || !videoRef.current) {
          return
        }

        const codeReader = new BrowserMultiFormatReader(undefined, {
          delayBetweenScanAttempts: 110,
          delayBetweenScanSuccess: 250,
        })
        codeReader.possibleFormats = [
          BarcodeFormat.EAN_13,
          BarcodeFormat.UPC_A,
          BarcodeFormat.UPC_E,
          BarcodeFormat.EAN_8,
        ]

        const controls = await codeReader.decodeFromStream(
          scannerStream,
          videoRef.current,
          (result, _error, controlsInCallback) => {
            if (!isMounted || detectedRef.current || !result) {
              return
            }

            const barcode = result.getText().replace(/\D/g, '')
            if (!/^\d{6,14}$/.test(barcode)) {
              return
            }

            detectedRef.current = true
            controlsInCallback.stop()
            onBarcodeDetected(barcode)
          },
        )

        controlsRef.current = controls
      } catch {
        if (isMounted) {
          onScannerError(CAMERA_PERMISSION_MESSAGE)
        }
      }
    }

    void startScanner()

    return () => {
      isMounted = false
      stopScanner()
    }
  }, [detectedBarcode, onBarcodeDetected, onScannerError, scannerStream, stopScanner])

  return createPortal(
    <motion.div
      className="scanner-backdrop"
      role="presentation"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0, transition: quickExit }}
      transition={{ duration: 0.18 }}
    >
      <motion.section
        className="scanner-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="barcode-scanner-title"
        initial={scaleFadeInitial}
        animate={scaleFadeEnter}
        exit={scaleFadeExit}
        transition={springSoft}
      >
        <div className="scanner-header">
          <div>
            <p className="eyebrow">Camera scanner</p>
            <h3 id="barcode-scanner-title">Scan packaged food barcode</h3>
          </div>
          <button type="button" className="icon-button" onClick={onClose} aria-label="Close scanner">
            <X size={18} aria-hidden="true" />
          </button>
        </div>

        <div className={`scanner-frame ${detectedBarcode ? 'captured' : ''}`}>
          {scannerError ? (
            <div className="scanner-message">
              <VideoOff size={28} aria-hidden="true" />
              <p>{scannerError}</p>
            </div>
          ) : detectedBarcode ? (
            <div className="scanner-message success">
              <CheckCircle2 size={30} aria-hidden="true" />
              <p>Barcode captured</p>
              <strong>{detectedBarcode}</strong>
            </div>
          ) : (
            <>
              <video ref={videoRef} muted playsInline aria-label="Live camera preview for barcode scanning" />
              <div className="scanner-reticle" aria-hidden="true">
                <ScanLine size={26} />
              </div>
              {!scannerStream ? (
                <div className="scanner-status">
                  <Loader2 size={17} className="spin" aria-hidden="true" />
                  Waiting for camera permission
                </div>
              ) : null}
            </>
          )}
        </div>

        <div className="scanner-footer">
          <p>Hold the EAN-13 or UPC barcode inside the frame. Manual entry stays available.</p>
          <div className="scanner-actions">
            <button type="button" className="secondary-button" onClick={onClose}>
              Stop scanner
            </button>
            <button
              type="button"
              className="primary-button"
              disabled={!detectedBarcode}
              onClick={() => onAnalyze(detectedBarcode)}
            >
              <Search size={17} aria-hidden="true" />
              Analyze this barcode
            </button>
          </div>
        </div>
      </motion.section>
    </motion.div>,
    document.body,
  )
}

function EmptyStartState() {
  return (
    <section className="empty-dashboard">
      <div>
        <p className="eyebrow">Ready for review</p>
        <h3>Start with a barcode</h3>
        <p>
          The dashboard will fill with overview data, nutrition flags, ingredient checks,
          additive and allergen sections, data quality warnings, and the AI reviewer report.
        </p>
      </div>
      <div className="empty-stat-row">
        <span>Rule-based flags</span>
        <span>Source warnings</span>
        <span>Saved history</span>
      </div>
    </section>
  )
}
