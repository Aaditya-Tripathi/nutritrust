const GROQ_API_KEY_STORAGE_KEY = 'nutritrust.groqApiKey'

export function readStoredGroqApiKey() {
  try {
    return window.localStorage.getItem(GROQ_API_KEY_STORAGE_KEY) || ''
  } catch {
    return ''
  }
}

export function writeStoredGroqApiKey(value: string) {
  window.localStorage.setItem(GROQ_API_KEY_STORAGE_KEY, value)
}

export function clearStoredGroqApiKey() {
  window.localStorage.removeItem(GROQ_API_KEY_STORAGE_KEY)
}
