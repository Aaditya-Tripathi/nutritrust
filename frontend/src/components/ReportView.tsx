import {
  AlertTriangle,
  Barcode,
  Bot,
  ChevronDown,
  CheckCircle2,
  ChevronRight,
  FlaskConical,
  Info,
  Leaf,
  ListChecks,
  PackageSearch,
  ShieldAlert,
  X,
} from 'lucide-react'
import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useMemo, useState } from 'react'
import { createPortal } from 'react-dom'
import type { ReactNode } from 'react'
import type {
  AdditiveFlag,
  AllergenFlag,
  DataQualityWarning,
  IngredientFlag,
  NormalizedReport,
  NutritionFlag,
  PositiveSignal,
} from '../types'
import {
  formatDate,
  hasAnyWarning,
  hasWarning,
  labelForMissing,
  toneForLevel,
} from '../reportUtils'
import {
  enter,
  exit,
  quickExit,
  routeInitial,
  springSoft,
  staggerContainer,
  staggerItem,
} from '../motion'

type ReportViewProps = {
  report: NormalizedReport
  actions?: ReactNode
}

type IngredientDisplayItem = {
  name: string
  details: string[]
}

type DetailInsight = {
  eyebrow: string
  title: string
  summary: string
  whatItIs?: string
  howItActs?: string
  tone?: 'neutral' | 'success' | 'warning' | 'danger'
  tags?: string[]
  facts?: Array<{ label: string; value: string }>
  sourceUrls?: string[]
}

type SnapshotTone = 'neutral' | 'success' | 'warning' | 'danger'

type SnapshotItem = {
  label: string
  value: string | number
  detail: string
  tone?: SnapshotTone
}

type FlagSectionId = 'nutrition' | 'ingredients' | 'additives' | 'allergens'

export function ReportView({ report, actions }: ReportViewProps) {
  const [selectedInsight, setSelectedInsight] = useState<DetailInsight | null>(null)
  const [expandedFlagSections, setExpandedFlagSections] = useState<Record<FlagSectionId, boolean>>({
    nutrition: false,
    ingredients: false,
    additives: false,
    allergens: false,
  })
  const ingredientNotEvaluated =
    report.ingredientFlags.length === 0 && hasWarning(report.dataQualityWarnings, 'ingredients')
  const additiveNotEvaluated =
    report.additiveFlags.length === 0 &&
    hasAnyWarning(report.dataQualityWarnings, ['ingredients', 'additives', 'sourceData'])
  const allergenNotEvaluated =
    report.allergenFlags.length === 0 && hasWarning(report.dataQualityWarnings, 'allergens')
  const ingredientDisplay = useMemo(
    () => (report.ingredientText ? formatIngredientDisplay(report.ingredientText) : null),
    [report.ingredientText],
  )
  const toggleFlagSection = (section: FlagSectionId) => {
    setExpandedFlagSections((current) => ({
      ...current,
      [section]: !current[section],
    }))
  }

  return (
    <motion.div
      className="report-stack"
      variants={staggerContainer}
      initial="hidden"
      animate="visible"
    >
      <motion.section className="overview-card" variants={staggerItem} transition={springSoft} layout>
        <div className="overview-main">
          <div className="overview-icon">
            <PackageSearch size={26} aria-hidden="true" />
          </div>
          <div>
            <p className="eyebrow">Product Overview</p>
            <h2>{labelForMissing(report.productName)}</h2>
            <div className="metadata-row">
              <span>{labelForMissing(report.brand)}</span>
              <span>{labelForMissing(report.category)}</span>
            </div>
          </div>
        </div>
        <div className="overview-tools">
          <div className="barcode-pill" aria-label={`Barcode ${report.barcode}`}>
            <Barcode size={17} aria-hidden="true" />
            <span>{report.barcode}</span>
          </div>
          {report.createdAt ? <span className="date-pill">{formatDate(report.createdAt)}</span> : null}
          {actions ? <div className="overview-actions">{actions}</div> : null}
        </div>
      </motion.section>

      <ReportSnapshot
        items={[
          {
            label: 'Nutrition Flags',
            value: report.nutritionFlags.length,
            detail: 'Per 100g threshold checks',
            tone: report.nutritionFlags.length > 0 ? 'warning' : 'neutral',
          },
          {
            label: 'Ingredient Flags',
            value: ingredientNotEvaluated ? 'Not evaluated' : report.ingredientFlags.length,
            detail: ingredientNotEvaluated ? 'Missing source ingredients' : 'Rule-based category matches',
            tone: ingredientNotEvaluated || report.ingredientFlags.length > 0 ? 'warning' : 'neutral',
          },
          {
            label: 'Additives',
            value: additiveNotEvaluated ? 'Not evaluated' : report.additiveFlags.length,
            detail: additiveNotEvaluated ? 'Limited additive source data' : 'Listed additive codes',
            tone: additiveNotEvaluated || report.additiveFlags.length > 0 ? 'warning' : 'neutral',
          },
          {
            label: 'Allergens',
            value: allergenNotEvaluated ? 'Not evaluated' : report.allergenFlags.length,
            detail: allergenNotEvaluated ? 'Missing allergen source data' : 'Listed allergen fields',
            tone: allergenNotEvaluated || report.allergenFlags.length > 0 ? 'danger' : 'neutral',
          },
          {
            label: 'Positive Signals',
            value: report.positiveSignals.length,
            detail: 'Favorable nutrition checks',
            tone: report.positiveSignals.length > 0 ? 'success' : 'neutral',
          },
          {
            label: 'Data Warnings',
            value: report.dataQualityWarnings.length,
            detail: 'Source completeness notes',
            tone: report.dataQualityWarnings.length > 0 ? 'warning' : 'success',
          },
        ]}
      />

      {ingredientDisplay ? (
        <section className="card-section">
          <div className="section-heading compact">
            <div>
              <p className="eyebrow">Label Text</p>
              <h3>Ingredient source used for checks</h3>
            </div>
            <span className="count-chip">{ingredientDisplay.primaryItems.length} items</span>
          </div>
          <div className="ingredient-text">
            <p className="ingredient-readable-line">{ingredientDisplay.readableText}</p>
            <div className="ingredient-summary">
              {ingredientDisplay.primaryItems.map((item) => (
                <button
                  type="button"
                  className={`ingredient-item ${item.details.length > 0 ? 'has-details' : ''} ${ingredientToneClass(item.name)}`}
                  key={`${item.name}-${item.details.join('|')}`}
                  onClick={() => setSelectedInsight(createIngredientInsight(item))}
                  aria-label={`View ingredient details for ${item.name}`}
                >
                  <div className="ingredient-body">
                    <strong>{item.name}</strong>
                    {item.details.length > 0 ? (
                      <div className="ingredient-detail-list">
                        {item.details.map((detail) => (
                          <span key={detail}>{detail}</span>
                        ))}
                      </div>
                    ) : null}
                  </div>
                  <ChevronRight size={16} aria-hidden="true" />
                </button>
              ))}
            </div>
          </div>
        </section>
      ) : null}

      <section className="dashboard-grid">
        <FlagSection
          title="Nutrition Flags"
          eyebrow="Per 100g thresholds"
          icon={<ListChecks size={20} aria-hidden="true" />}
          flags={report.nutritionFlags}
          expanded={expandedFlagSections.nutrition}
          onToggle={() => toggleFlagSection('nutrition')}
          emptyText="No nutrition flags generated from available structured values."
          renderItem={(flag) => (
            <NutritionFlagCard
              key={`${flag.name}-${flag.value}`}
              flag={flag}
              onSelect={() => setSelectedInsight(createNutritionInsight(flag))}
            />
          )}
        />

        <SignalSection
          title="Positive Signals"
          eyebrow="Rule-based positives"
          icon={<CheckCircle2 size={20} aria-hidden="true" />}
          signals={report.positiveSignals}
          onSelectSignal={(signal) => setSelectedInsight(createPositiveSignalInsight(signal))}
        />
      </section>

      <section className="triage-grid">
        <FlagSection
          title="Ingredient Flags"
          eyebrow="Ingredient categories"
          icon={<Leaf size={20} aria-hidden="true" />}
          flags={report.ingredientFlags}
          expanded={expandedFlagSections.ingredients}
          onToggle={() => toggleFlagSection('ingredients')}
          notEvaluated={ingredientNotEvaluated}
          emptyText="No ingredient category flags detected from available ingredient data."
          renderItem={(flag) => (
            <IngredientFlagCard
              key={flag.category}
              flag={flag}
              onSelect={() => setSelectedInsight(createIngredientFlagInsight(flag))}
            />
          )}
        />

        <FlagSection
          title="Additive Flags"
          eyebrow="Additive code checks"
          icon={<FlaskConical size={20} aria-hidden="true" />}
          flags={report.additiveFlags}
          expanded={expandedFlagSections.additives}
          onToggle={() => toggleFlagSection('additives')}
          notEvaluated={additiveNotEvaluated}
          emptyText="No additive flags detected from available product data."
          renderItem={(flag) => (
            <SourceFlagCard
              key={`${flag.name}-${flag.source}`}
              flag={flag}
              onSelect={() => setSelectedInsight(createSourceFlagInsight(flag, 'Additive'))}
            />
          )}
        />

        <FlagSection
          title="Allergen Flags"
          eyebrow="Allergen and trace checks"
          icon={<ShieldAlert size={20} aria-hidden="true" />}
          flags={report.allergenFlags}
          expanded={expandedFlagSections.allergens}
          onToggle={() => toggleFlagSection('allergens')}
          notEvaluated={allergenNotEvaluated}
          emptyText="No allergen flags detected from available product data."
          renderItem={(flag) => (
            <SourceFlagCard
              key={`${flag.name}-${flag.source}`}
              flag={flag}
              onSelect={() => setSelectedInsight(createSourceFlagInsight(flag, 'Allergen'))}
            />
          )}
        />
      </section>

      <section className="dashboard-grid">
        <DataQualitySection
          warnings={report.dataQualityWarnings}
          onSelectWarning={(warning) => setSelectedInsight(createWarningInsight(warning))}
        />
        <AIReportCard text={report.aiReport} />
      </section>

      <AnimatePresence initial={false}>
        {selectedInsight ? (
          <DetailDrawer
            key={`${selectedInsight.eyebrow}-${selectedInsight.title}`}
            insight={selectedInsight}
            onClose={() => setSelectedInsight(null)}
          />
        ) : null}
      </AnimatePresence>
    </motion.div>
  )
}

function ReportSnapshot({ items }: { items: SnapshotItem[] }) {
  return (
    <motion.section
      className="snapshot-strip"
      aria-label="Report snapshot"
      variants={staggerContainer}
      transition={springSoft}
    >
      {items.map((item) => (
        <motion.div
          className={`snapshot-tile ${item.tone ?? 'neutral'}`}
          key={item.label}
          variants={staggerItem}
          transition={springSoft}
          layout
        >
          <p>{item.label}</p>
          <strong>{item.value}</strong>
          <span>{item.detail}</span>
        </motion.div>
      ))}
    </motion.section>
  )
}

function accordionItemKey(flag: unknown, index: number) {
  if (flag && typeof flag === 'object') {
    const record = flag as Record<string, unknown>
    const stableParts = [
      record.id,
      record.name,
      record.category,
      record.source,
      record.value,
      record.message,
    ].filter(Boolean)

    if (stableParts.length > 0) {
      return stableParts.join('-')
    }
  }

  return `flag-${index}`
}

function FlagSection<T>({
  title,
  eyebrow,
  icon,
  flags,
  emptyText,
  notEvaluated,
  expanded,
  onToggle,
  renderItem,
}: {
  title: string
  eyebrow: string
  icon: ReactNode
  flags: T[]
  emptyText: string
  notEvaluated?: boolean
  expanded: boolean
  onToggle: () => void
  renderItem: (flag: T) => ReactNode
}) {
  return (
    <motion.section
      className={`card-section flag-accordion-section ${expanded ? 'expanded' : ''}`}
      layout
      variants={staggerItem}
      transition={springSoft}
    >
      <button
        type="button"
        className="section-heading accordion-section-trigger"
        onClick={onToggle}
        aria-expanded={expanded}
      >
        <div className="section-title">
          <span className="section-icon">{icon}</span>
          <div>
            <p className="eyebrow">{eyebrow}</p>
            <h3>{title}</h3>
          </div>
        </div>
        <span className="accordion-meta">
          <span className="count-chip">{flags.length}</span>
          <ChevronDown size={18} aria-hidden="true" />
        </span>
      </button>

      <AnimatePresence initial={false} mode="wait">
        {!expanded ? (
          <motion.p
            className="accordion-preview"
            key="preview"
            initial={routeInitial}
            animate={enter}
            exit={exit}
            transition={springSoft}
          >
            {notEvaluated
              ? 'Source data is incomplete. Open this section for details.'
              : flags.length > 0
                ? `Open to review ${flags.length} generated ${title.toLowerCase()}.`
                : emptyText}
          </motion.p>
        ) : (
          <motion.div
            className="accordion-motion-body"
            key="content"
            initial={{ height: 0, opacity: 0, filter: 'blur(4px)' }}
            animate={{ height: 'auto', opacity: 1, filter: 'blur(0px)' }}
            exit={{ height: 0, opacity: 0, filter: 'blur(4px)', transition: quickExit }}
            transition={springSoft}
          >
            {notEvaluated ? (
              <EmptyNotice tone="warning" title="Not evaluated" text="Source data was missing or incomplete for this section." />
            ) : flags.length > 0 ? (
              <motion.div
                className="flag-list accordion-flag-list"
                variants={staggerContainer}
                initial="hidden"
                animate="visible"
              >
                {flags.map((flag, index) => (
                  <motion.div key={accordionItemKey(flag, index)} variants={staggerItem} transition={springSoft}>
                    {renderItem(flag)}
                  </motion.div>
                ))}
              </motion.div>
            ) : (
              <EmptyNotice tone="neutral" title="None detected" text={emptyText} />
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </motion.section>
  )
}

function NutritionFlagCard({ flag, onSelect }: { flag: NutritionFlag; onSelect: () => void }) {
  return (
    <button type="button" className="flag-card interactive-card" onClick={onSelect}>
      <div className="flag-card-top">
        <strong>{formatFlagName(flag.name)}</strong>
        <span className={`badge ${toneForLevel(flag.level)}`}>{flag.level}</span>
      </div>
      <p className="flag-value">{flag.value}</p>
      <p>{flag.explanation}</p>
    </button>
  )
}

function IngredientFlagCard({ flag, onSelect }: { flag: IngredientFlag; onSelect: () => void }) {
  const visibleTerms = flag.matchedTerms.slice(0, 5)
  const hiddenTermCount = Math.max(0, flag.matchedTerms.length - visibleTerms.length)

  return (
    <button type="button" className="flag-card interactive-card" onClick={onSelect}>
      <div className="flag-card-top">
        <strong>{flag.category}</strong>
        <span className="badge warning">{flag.matchedTerms.length} terms</span>
      </div>
      <div className="term-row">
        {visibleTerms.map((term) => (
          <span key={term}>{term}</span>
        ))}
        {hiddenTermCount > 0 ? <span className="term-overflow">+{hiddenTermCount} more</span> : null}
      </div>
      <p>{flag.explanation}</p>
    </button>
  )
}

function SourceFlagCard({
  flag,
  onSelect,
}: {
  flag: AdditiveFlag | AllergenFlag
  onSelect: () => void
}) {
  return (
    <button type="button" className="flag-card interactive-card" onClick={onSelect}>
      <div className="flag-card-top">
        <strong>{formatFlagName(flag.name)}</strong>
        <span className="source-chip">{formatSourceLabel(flag.source)}</span>
      </div>
      <p>{flag.explanation}</p>
    </button>
  )
}

function SignalSection({
  title,
  eyebrow,
  icon,
  signals,
  onSelectSignal,
}: {
  title: string
  eyebrow: string
  icon: ReactNode
  signals: PositiveSignal[]
  onSelectSignal: (signal: PositiveSignal) => void
}) {
  return (
    <section className="card-section">
      <div className="section-heading">
        <div className="section-title">
          <span className="section-icon success">{icon}</span>
          <div>
            <p className="eyebrow">{eyebrow}</p>
            <h3>{title}</h3>
          </div>
        </div>
        <span className="count-chip">{signals.length}</span>
      </div>

      {signals.length > 0 ? (
        <div className="flag-list">
          {signals.map((signal) => (
            <button
              type="button"
              className="flag-card interactive-card"
              key={`${signal.name}-${signal.value}`}
              onClick={() => onSelectSignal(signal)}
            >
              <div className="flag-card-top">
                <strong>{signal.name}</strong>
                <span className="badge success">{signal.level}</span>
              </div>
              <p className="flag-value">{signal.value}</p>
              <p>{signal.explanation}</p>
            </button>
          ))}
        </div>
      ) : (
        <EmptyNotice
          tone="neutral"
          title="No positive signals"
          text="No favorable nutrition signals were generated from available values."
        />
      )}
    </section>
  )
}

function DataQualitySection({
  warnings,
  onSelectWarning,
}: {
  warnings: DataQualityWarning[]
  onSelectWarning: (warning: DataQualityWarning) => void
}) {
  return (
    <section className="card-section warning-section">
      <div className="section-heading">
        <div className="section-title">
          <span className="section-icon warning">
            <AlertTriangle size={20} aria-hidden="true" />
          </span>
          <div>
            <p className="eyebrow">Data Quality Warnings</p>
            <h3>Source completeness</h3>
          </div>
        </div>
        <span className="count-chip">{warnings.length}</span>
      </div>

      {warnings.length > 0 ? (
        <div className="warning-list">
          {warnings.map((warning) => (
            <button
              type="button"
              className="warning-item interactive-card"
              key={`${warning.field}-${warning.message}`}
              onClick={() => onSelectWarning(warning)}
            >
              <span>{formatWarningField(warning.field)}</span>
              <p>{warning.message}</p>
            </button>
          ))}
        </div>
      ) : (
        <EmptyNotice
          tone="success"
          title="No major warnings"
          text="No data completeness warnings were generated for this report."
        />
      )}
    </section>
  )
}

function DetailDrawer({ insight, onClose }: { insight: DetailInsight; onClose: () => void }) {
  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onClose()
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  return createPortal(
    <motion.div
      className="detail-drawer-backdrop"
      role="presentation"
      onClick={onClose}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0, transition: quickExit }}
      transition={{ duration: 0.16 }}
    >
      <motion.aside
        className={`detail-drawer ${insight.tone ?? 'neutral'}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby="detail-drawer-title"
        onClick={(event) => event.stopPropagation()}
        initial={{ opacity: 0, x: 18, filter: 'blur(4px)' }}
        animate={{ opacity: 1, x: 0, filter: 'blur(0px)' }}
        exit={{ opacity: 0, x: 12, filter: 'blur(4px)', transition: quickExit }}
        transition={springSoft}
      >
        <div className="detail-drawer-header">
          <div>
            <p className="eyebrow">{insight.eyebrow}</p>
            <h3 id="detail-drawer-title">{insight.title}</h3>
          </div>
          <button type="button" className="icon-button" onClick={onClose} aria-label="Close details">
            <X size={18} aria-hidden="true" />
          </button>
        </div>

        <p className="detail-summary">{insight.summary}</p>

        {insight.whatItIs ? (
          <div className="detail-block">
            <p className="detail-block-label">What it is</p>
            <p className="detail-prose">{insight.whatItIs}</p>
          </div>
        ) : null}

        {insight.howItActs ? (
          <div className="detail-block">
            <p className="detail-block-label">How it acts</p>
            <p className="detail-prose">{insight.howItActs}</p>
          </div>
        ) : null}

        {insight.tags && insight.tags.length > 0 ? (
          <div className="detail-block">
            <p className="detail-block-label">Matched or nested label items</p>
            <div className="detail-tag-row">
              {insight.tags.map((tag) => (
                <span key={tag}>{tag}</span>
              ))}
            </div>
          </div>
        ) : null}

        {insight.facts && insight.facts.length > 0 ? (
          <div className="detail-block">
            <p className="detail-block-label">Review facts</p>
            <dl className="detail-facts">
              {insight.facts.map((fact) => (
                <div key={`${fact.label}-${fact.value}`}>
                  <dt>{fact.label}</dt>
                  <dd>{fact.value}</dd>
                </div>
              ))}
            </dl>
          </div>
        ) : null}

        {insight.sourceUrls && insight.sourceUrls.length > 0 ? (
          <div className="detail-block">
            <p className="detail-block-label">Taxonomy source</p>
            <div className="detail-source-list">
              {insight.sourceUrls.map((url) => (
                <a href={url} target="_blank" rel="noreferrer" key={url}>
                  {formatSourceUrl(url)}
                </a>
              ))}
            </div>
          </div>
        ) : null}
      </motion.aside>
    </motion.div>,
    document.body,
  )
}

function AIReportCard({ text }: { text: string }) {
  const sections = formatAIReport(text)

  return (
    <section className="ai-card">
      <div className="section-heading">
        <div className="section-title">
          <span className="section-icon ai">
            <Bot size={20} aria-hidden="true" />
          </span>
          <div>
            <p className="eyebrow">AI Report</p>
            <h3>Reviewer narrative</h3>
          </div>
        </div>
      </div>
      <div className="ai-copy">
        {sections.map((section) => (
          <article className="ai-section" key={section.title}>
            <h4>{section.title}</h4>
            {section.body.map((paragraph) => (
              <p key={paragraph}>{paragraph}</p>
            ))}
          </article>
        ))}
      </div>
    </section>
  )
}

function formatWarningField(field: string) {
  const labels: Record<string, string> = {
    ingredients: 'Ingredients',
    nutrition: 'Nutrition',
    allergens: 'Allergens',
    additives: 'Additives',
    sourceData: 'Source Data',
    productDetails: 'Product Details',
    manualNutritionNote: 'Manual Nutrition Note',
  }

  if (labels[field]) {
    return labels[field]
  }

  return field
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .replace(/\b\w/g, (letter) => letter.toUpperCase())
}

function formatSourceLabel(source: string) {
  const labels: Record<string, string> = {
    additives_tags: 'Additive Data',
    allergen_fields: 'Allergen Data',
    ingredients_text: 'Ingredient Text',
    ingredients: 'Ingredients',
    allergens: 'Allergens',
    traces: 'Trace Fields',
    sourceData: 'Source Data',
  }

  if (labels[source]) {
    return labels[source]
  }

  return formatWarningField(source)
}

function formatFlagName(name: string) {
  if (/^e\d+[a-z]?$/i.test(name)) {
    return name.toUpperCase()
  }

  return name
}

function formatSourceUrl(url: string) {
  try {
    const parsed = new URL(url)
    if (parsed.hostname.includes('wikidata.org')) {
      return 'Wikidata'
    }
    if (parsed.hostname.includes('wikipedia.org')) {
      return 'Wikipedia'
    }
    return parsed.hostname.replace(/^www\./, '')
  } catch {
    return url
  }
}

function createIngredientInsight(item: IngredientDisplayItem): DetailInsight {
  const ingredientInfo = describeIngredient(item.name)

  return {
    eyebrow: 'Ingredient Detail',
    title: item.name,
    tone: ingredientInfo.tone,
    summary: ingredientInfo.summary,
    tags: item.details,
    facts: [
      { label: 'Display group', value: ingredientInfo.group },
      { label: 'Source', value: 'Product label / Open Food Facts ingredient text' },
      {
        label: 'How to interpret',
        value:
          'Use this as label evidence only. NutriTrust does not classify individual ingredients as harmful.',
      },
    ],
  }
}

function describeIngredient(name: string) {
  const normalized = name.toLowerCase()

  if (/sugar|sweetener|syrup|jaggery|glucose|fructose/.test(normalized)) {
    return {
      group: 'Sweetener source',
      tone: 'warning' as const,
      summary:
        'This ingredient is presented as a sugar or sweetener-related source in the label text. NutriTrust uses it as factual label evidence for ingredient-category checks.',
    }
  }

  if (/pulp|juice|fruit|mango|papaya|pear|apple|banana|pineapple|orange|grape/.test(normalized)) {
    return {
      group: 'Fruit or plant ingredient',
      tone: 'success' as const,
      summary:
        'This appears to be a fruit or plant-derived ingredient listed on the product label. If it contains nested items, those sub-items are shown below exactly as parsed from the label.',
    }
  }

  if (/acidity regulator|thickener|preservative|colour|color|emulsifier|raising agent|stabilizer|flavour|flavor|e\d+/i.test(name)) {
    return {
      group: 'Functional ingredient or additive',
      tone: 'warning' as const,
      summary:
        'This ingredient appears to perform a functional role such as preservation, texture, colour, flavour, acidity regulation, emulsification, or raising. The app reports it factually from available label data.',
    }
  }

  if (/salt|mineral|vitamin/.test(normalized)) {
    return {
      group: 'Mineral, vitamin, or seasoning',
      tone: 'neutral' as const,
      summary:
        'This ingredient is listed as a mineral, vitamin, or seasoning component in the label text.',
    }
  }

  if (/milk|wheat|soy|soya|gluten|nut|peanut/.test(normalized)) {
    return {
      group: 'Potential allergen-related ingredient',
      tone: 'danger' as const,
      summary:
        'This ingredient text matches a common allergen-related term. Use the allergen flags and source data warnings for the final allergen review.',
    }
  }

  return {
    group: 'Label ingredient',
    tone: 'neutral' as const,
    summary:
      'This ingredient appears in the source label text used by the backend checks. NutriTrust displays it as factual label evidence, not as a risk judgement.',
  }
}

function ingredientToneClass(name: string) {
  return `ingredient-${describeIngredient(name).tone}`
}

function createNutritionInsight(flag: NutritionFlag): DetailInsight {
  return {
    eyebrow: 'Nutrition Flag',
    title: formatFlagName(flag.name),
    tone: detailToneForLevel(flag.level),
    summary: flag.explanation,
    whatItIs: flag.description,
    howItActs: flag.functionDescription,
    sourceUrls: flag.sourceUrls,
    facts: [
      { label: 'Level', value: flag.level },
      { label: 'Measured value', value: flag.value },
      { label: 'Basis', value: 'Rule-based threshold check per 100g where available.' },
    ],
  }
}

function detailToneForLevel(level: string): DetailInsight['tone'] {
  const tone = toneForLevel(level)
  if (tone === 'danger' || tone === 'warning' || tone === 'success') {
    return tone
  }
  return 'neutral'
}

function createIngredientFlagInsight(flag: IngredientFlag): DetailInsight {
  return {
    eyebrow: 'Ingredient Flag',
    title: flag.category,
    tone: 'warning',
    summary: flag.explanation,
    whatItIs: flag.description,
    howItActs: flag.functionDescription,
    tags: [...(flag.matchedTerms ?? []), ...(flag.matchedTaxonomyIds ?? [])],
    sourceUrls: flag.sourceUrls,
    facts: [
      ...(flag.displayName ? [{ label: 'Taxonomy display', value: flag.displayName }] : []),
      ...(flag.taxonomyId ? [{ label: 'Taxonomy ID', value: flag.taxonomyId }] : []),
      ...(flag.classes && flag.classes.length > 0
        ? [{ label: 'Classes', value: flag.classes.join(', ') }]
        : []),
      { label: 'Matched terms', value: flag.matchedTerms.join(', ') || 'None' },
      { label: 'Basis', value: 'Rule-based text match against the available ingredient label.' },
    ],
  }
}

function createSourceFlagInsight(flag: AdditiveFlag | AllergenFlag, label: 'Additive' | 'Allergen'): DetailInsight {
  return {
    eyebrow: `${label} Detail`,
    title: flag.displayName ?? formatFlagName(flag.name),
    tone: label === 'Allergen' ? 'danger' : 'warning',
    summary: flag.explanation,
    whatItIs: flag.description,
    howItActs: flag.functionDescription,
    tags: flag.matchedTerms,
    sourceUrls: flag.sourceUrls,
    facts: [
      ...(flag.displayName ? [{ label: 'Display name', value: flag.displayName }] : []),
      ...(flag.taxonomyId ? [{ label: 'Taxonomy ID', value: flag.taxonomyId }] : []),
      ...(flag.classes && flag.classes.length > 0
        ? [{ label: 'Classes', value: flag.classes.join(', ') }]
        : []),
      ...(flag.matchedTaxonomyIds && flag.matchedTaxonomyIds.length > 0
        ? [{ label: 'Matched taxonomy IDs', value: flag.matchedTaxonomyIds.join(', ') }]
        : []),
      { label: 'Source field', value: formatSourceLabel(flag.source) },
      {
        label: 'Interpretation',
        value:
          label === 'Allergen'
            ? 'Listed allergen or trace item from available product data.'
            : 'Listed additive code from available product data.',
      },
    ],
  }
}

function createPositiveSignalInsight(signal: PositiveSignal): DetailInsight {
  return {
    eyebrow: 'Positive Signal',
    title: signal.name,
    tone: 'success',
    summary: signal.explanation,
    facts: [
      { label: 'Level', value: signal.level },
      { label: 'Measured value', value: signal.value },
      { label: 'Basis', value: 'Rule-based favorable nutrition signal from available structured values.' },
    ],
  }
}

function createWarningInsight(warning: DataQualityWarning): DetailInsight {
  return {
    eyebrow: 'Data Quality',
    title: formatWarningField(warning.field),
    tone: 'warning',
    summary: warning.message,
    facts: [
      { label: 'Affected field', value: formatWarningField(warning.field) },
      {
        label: 'Reviewer impact',
        value: 'Treat related flags as limited by source data completeness.',
      },
    ],
  }
}

function formatIngredientDisplay(text: string) {
  const cleaned = getReadableIngredientSource(text)
  const rawParts = splitIngredientParts(cleaned)

  const seen = new Set<string>()
  const primaryItems = rawParts.reduce<Array<{ name: string; details: string[] }>>((items, part) => {
    const item = formatIngredientPart(part)
    const key = `${item.name}|${item.details.join('|')}`.toLowerCase()

    if (item.name && !seen.has(key)) {
      seen.add(key)
      items.push(item)
    }

    return items
  }, [])

  return {
    primaryItems: primaryItems.length > 0 ? primaryItems : [{ name: cleaned, details: [] }],
    readableText: formatReadableIngredientText(cleaned),
  }
}

function getReadableIngredientSource(text: string) {
  const withoutHtml = text
    .replace(/<[^>]*>/g, ' ')
    .replace(/_/g, ' ')
    .replace(/\s+-\s+/g, ' ')
    .replace(/\s+/g, ' ')
    .replace(/\s+,/g, ',')
    .trim()

  const tagStartIndex = withoutHtml.search(/\s\b[a-z]{2}:[\w-]+/i)
  const withoutTags =
    tagStartIndex >= 0 ? withoutHtml.slice(0, tagStartIndex).trim() : withoutHtml
  const servingInfoIndex = withoutTags.search(/\b(?:NUMBER OF SERVES|PER SERVE|APPROX\.?)\b/i)
  const withoutServingInfo =
    servingInfoIndex > 0 ? withoutTags.slice(0, servingInfoIndex).trim() : withoutTags

  const duplicateIndex = findRepeatedSourceIndex(withoutServingInfo)
  const readableSource =
    duplicateIndex > 0
      ? withoutServingInfo.slice(0, duplicateIndex).trim()
      : withoutServingInfo

  return readableSource.replace(/[.;]\s*$/, '').trim()
}

function formatReadableIngredientText(text: string) {
  return text
    .replace(/\s*\(\s*/g, ' (')
    .replace(/\s*\)\s*/g, ') ')
    .replace(/\s+,/g, ',')
    .replace(/\s+/g, ' ')
    .trim()
}

function findRepeatedSourceIndex(text: string) {
  const sourceStart = text.slice(0, 48).trim()
  if (sourceStart.length < 24) {
    return -1
  }

  return text.indexOf(sourceStart, sourceStart.length)
}

function splitIngredientParts(text: string) {
  const parts: string[] = []
  let current = ''
  let parenthesisDepth = 0

  for (let index = 0; index < text.length; index += 1) {
    const character = text[index]

    if (character === '(') {
      parenthesisDepth += 1
    }

    if (character === ')') {
      parenthesisDepth = Math.max(0, parenthesisDepth - 1)
    }

    const nextCharacter = text[index + 1] || ''
    const nextWord = text.slice(index + 1).trimStart()
    const shouldSplit =
      parenthesisDepth === 0 &&
      (character === ',' ||
        character === ';' ||
        (character === '.' && /\s/.test(nextCharacter) && /^[A-Z0-9]/.test(nextWord)))

    if (shouldSplit) {
      if (current.trim()) {
        parts.push(current.trim())
      }
      current = ''
      continue
    }

    current += character
  }

  if (current.trim()) {
    parts.push(current.trim())
  }

  return parts
}

function formatIngredientPart(part: string) {
  const cleaned = part
    .replace(/\s+-\s+/g, ' ')
    .replace(/[.;]\s*$/g, '')
    .replace(/\s+/g, ' ')
    .trim()

  if (!cleaned) {
    return { name: '', details: [] }
  }

  const groupedIngredient = cleaned.match(/^(.+?)\s*\((.+)\)$/)
  if (groupedIngredient && groupedIngredient[2].includes(',')) {
    return {
      name: formatIngredientName(groupedIngredient[1]),
      details: splitIngredientParts(groupedIngredient[2])
        .map((detail) => formatIngredientName(detail))
        .filter(Boolean),
    }
  }

  return { name: formatIngredientName(cleaned), details: [] }
}

function formatIngredientName(name: string) {
  const cleaned = name.replace(/\s+/g, ' ').trim()

  if (!cleaned) {
    return ''
  }

  return cleaned
    .toLowerCase()
    .replace(/\b([a-z])/g, (letter) => letter.toUpperCase())
    .replace(/\bE(\d+)/g, 'E$1')
    .replace(/\bFssai\b/g, 'FSSAI')
}

function formatAIReport(text: string) {
  const trimmed = text.trim()
  if (!trimmed) {
    return [{ title: 'Reviewer Note', body: ['No reviewer narrative was saved for this report. Please review the factual flags.'] }]
  }

  if (trimmed === 'AI explanation unavailable. Please review the factual flags.') {
    return [
      {
        title: 'Reviewer Note',
        body: ['This saved report was generated before the local fallback narrative was available. Regenerate the report to create a reviewer narrative from the factual flags.'],
      },
    ]
  }

  if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
    return [
      {
        title: 'Reviewer Note',
        body: ['AI report text could not be displayed as a narrative. Please review the factual flags.'],
      },
    ]
  }

  const sections: Array<{ title: string; body: string[] }> = []
  let currentTitle = 'Reviewer Narrative'
  let currentBody: string[] = []

  for (const line of trimmed.split(/\r?\n/)) {
    const cleanLine = line.trim()
    if (!cleanLine) {
      continue
    }

    if (/^[A-Za-z][A-Za-z\s&-]{2,}:$/.test(cleanLine)) {
      if (currentBody.length > 0 || sections.length > 0) {
        sections.push({ title: currentTitle, body: currentBody })
      }
      currentTitle = cleanLine.replace(/:$/, '')
      currentBody = []
      continue
    }

    currentBody.push(cleanLine)
  }

  sections.push({ title: currentTitle, body: currentBody })
  return sections.filter((section) => section.body.length > 0)
}

function EmptyNotice({
  tone,
  title,
  text,
}: {
  tone: 'neutral' | 'warning' | 'success'
  title: string
  text: string
}) {
  return (
    <div className={`empty-notice ${tone}`}>
      <Info size={18} aria-hidden="true" />
      <div>
        <strong>{title}</strong>
        <p>{text}</p>
      </div>
    </div>
  )
}
