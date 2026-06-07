import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import {
  AlertCircle,
  CheckCircle2,
  ExternalLink,
  KeyRound,
  Loader2,
  LockKeyhole,
  PlugZap,
  Save,
  ShieldCheck,
  Trash2,
} from 'lucide-react'
import { testGroqApiKey } from '../services/api'
import {
  clearStoredGroqApiKey,
  readStoredGroqApiKey,
  writeStoredGroqApiKey,
} from '../groqKeyStorage'

const setupSteps = [
  'Open Groq Console and sign in or create an account.',
  'Go to API Keys from the console menu.',
  'Create a new API key and copy it once Groq shows it.',
  'Paste it here and save it before generating reports.',
]
const readmeUrl = 'https://github.com/Aaditya-Tripathi/nutritrust#groq-api-key-setup'

export function GroqApiKey() {
  const [groqApiKey, setGroqApiKey] = useState(readStoredGroqApiKey)
  const [status, setStatus] = useState<'empty' | 'saved' | 'editing'>(
    () => (readStoredGroqApiKey() ? 'saved' : 'empty'),
  )
  const testMutation = useMutation({
    mutationFn: testGroqApiKey,
  })

  function saveGroqApiKey() {
    const normalizedKey = groqApiKey.trim()
    if (!normalizedKey) {
      removeGroqApiKey()
      return
    }

    try {
      writeStoredGroqApiKey(normalizedKey)
      setGroqApiKey(normalizedKey)
      setStatus('saved')
      testMutation.reset()
    } catch {
      setStatus('editing')
    }
  }

  function removeGroqApiKey() {
    try {
      clearStoredGroqApiKey()
    } finally {
      setGroqApiKey('')
      setStatus('empty')
      testMutation.reset()
    }
  }

  function testCurrentGroqApiKey() {
    testMutation.mutate(groqApiKey.trim())
  }

  const testResult = testMutation.data
  const testStateClass = testMutation.isPending
    ? 'pending'
    : testResult?.ok
      ? 'success'
      : testMutation.error || testResult
        ? 'error'
        : 'idle'

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <p className="eyebrow">Groq API Key</p>
          <h2>Connect AI narrative generation</h2>
          <p>
            Save a Groq key on this device so NutriTrust can request reviewer text
            while keeping factual product checks rule-based.
          </p>
        </div>
      </header>

      <section className="groq-key-grid">
        <div className="card-section groq-key-card">
          <div className="section-heading">
            <div className="section-title">
              <span className="section-icon ai">
                <KeyRound size={20} aria-hidden="true" />
              </span>
              <div>
                <p className="eyebrow">Saved Key</p>
                <h3>Browser storage</h3>
              </div>
            </div>
            <span className={`key-state-chip ${status}`}>
              {status === 'saved' ? 'Saved' : status === 'editing' ? 'Unsaved' : 'Empty'}
            </span>
          </div>

          <label className="groq-key-field" htmlFor="groqApiKey">
            API key
            <input
              id="groqApiKey"
              type="password"
              autoComplete="off"
              spellCheck={false}
              placeholder="gsk_..."
              value={groqApiKey}
              onChange={(event) => {
                setGroqApiKey(event.target.value)
                setStatus(event.target.value.trim() ? 'editing' : 'empty')
              }}
            />
          </label>

          <div className="groq-key-actions">
            <button type="button" className="primary-button" onClick={saveGroqApiKey}>
              <Save size={17} aria-hidden="true" />
              Save key
            </button>
            <button
              type="button"
              className="secondary-button"
              disabled={testMutation.isPending}
              onClick={testCurrentGroqApiKey}
            >
              {testMutation.isPending ? (
                <Loader2 size={17} className="spin" aria-hidden="true" />
              ) : (
                <PlugZap size={17} aria-hidden="true" />
              )}
              Test key
            </button>
            <button type="button" className="secondary-button danger" onClick={removeGroqApiKey}>
              <Trash2 size={17} aria-hidden="true" />
              Clear key
            </button>
          </div>

          <div className={`groq-test-result ${testStateClass}`}>
            {testStateClass === 'success' ? (
              <CheckCircle2 size={18} aria-hidden="true" />
            ) : testStateClass === 'pending' ? (
              <Loader2 size={18} className="spin" aria-hidden="true" />
            ) : testStateClass === 'error' ? (
              <AlertCircle size={18} aria-hidden="true" />
            ) : (
              <PlugZap size={18} aria-hidden="true" />
            )}
            <p>
              {testMutation.isPending
                ? 'Testing Groq connection...'
                : testMutation.error
                  ? testMutation.error.message
                  : testResult?.message ?? 'Use Test key to confirm Groq accepts the saved key.'}
            </p>
          </div>

          <div className="groq-key-note">
            <LockKeyhole size={18} aria-hidden="true" />
            <p>
              The key is stored in this browser only. Report generation sends it to the
              backend for that request, and the backend does not save it in PostgreSQL.
            </p>
          </div>
        </div>

        <div className="card-section groq-setup-card">
          <div className="section-heading">
            <div className="section-title">
              <span className="section-icon success">
                <ShieldCheck size={20} aria-hidden="true" />
              </span>
              <div>
                <p className="eyebrow">Setup Steps</p>
                <h3>Get a Groq key</h3>
              </div>
            </div>
          </div>

          <ol className="groq-step-list">
            {setupSteps.map((step) => (
              <li key={step}>{step}</li>
            ))}
          </ol>

          <div className="groq-link-row">
            <a className="primary-button" href="https://console.groq.com/keys" target="_blank" rel="noreferrer">
              <ExternalLink size={17} aria-hidden="true" />
              Open Groq keys
            </a>
            <a className="secondary-button" href="https://console.groq.com/docs/quickstart" target="_blank" rel="noreferrer">
              <ExternalLink size={17} aria-hidden="true" />
              Quickstart docs
            </a>
          </div>

          <p className="groq-readme-note">
            If you cannot set this up from here, refer to the instructions in the{' '}
            <a href={readmeUrl} target="_blank" rel="noreferrer">
              GitHub README file
            </a>
            .
          </p>
        </div>
      </section>

    </div>
  )
}
