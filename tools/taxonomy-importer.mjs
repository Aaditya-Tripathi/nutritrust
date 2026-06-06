import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const TAXONOMIES = {
  additives: 'https://raw.githubusercontent.com/openfoodfacts/openfoodfacts-server/main/taxonomies/additives.txt',
  'additive-classes': 'https://raw.githubusercontent.com/openfoodfacts/openfoodfacts-server/main/taxonomies/additives_classes.txt',
  allergens: 'https://raw.githubusercontent.com/openfoodfacts/openfoodfacts-server/main/taxonomies/allergens.txt',
  ingredients: 'https://raw.githubusercontent.com/openfoodfacts/openfoodfacts-server/main/taxonomies/food/ingredients.txt',
}

const OUTPUT_DIR = path.resolve('src/main/resources/openfoodfacts-taxonomies')

export function parseTaxonomy(text, kind) {
  const entries = []

  for (const block of splitBlocks(text)) {
    const lines = block
      .split(/\r?\n/)
      .map((line) => stripComment(line).trim())
      .filter(Boolean)

    if (lines.length === 0) {
      continue
    }

    const enLine = lines.find((line) => line.startsWith('en:'))
    if (!enLine) {
      continue
    }

    const names = readListProperty(enLine, 'en:')
    if (names.length === 0) {
      continue
    }

    const firstName = names[0]
    const id = canonicalId(firstName, kind)
    if (!id) {
      continue
    }

    const entry = {
      id,
      taxonomy: kind,
      names: [firstName],
      aliases: names.slice(1),
      codes: extractCodes(names, lines),
      parents: [],
      classes: [],
      allergens: [],
      nova: null,
      fromPalmOil: null,
      vegan: null,
      vegetarian: null,
      description: null,
      wikipediaUrl: null,
      wikidataId: null,
    }

    for (const line of lines) {
      if (line.startsWith('<')) {
        entry.parents.push(...readParents(line))
      } else if (line.startsWith('additives_classes:en:')) {
        entry.classes.push(...readListProperty(line, 'additives_classes:en:').map(canonicalTaxonomyReference))
      } else if (line.startsWith('mandatory_additive_class:en:')) {
        entry.classes.push(...readListProperty(line, 'mandatory_additive_class:en:').map(canonicalTaxonomyReference))
      } else if (line.startsWith('allergens:en:')) {
        entry.allergens.push(...readListProperty(line, 'allergens:en:').map(canonicalTaxonomyReference))
      } else if (line.startsWith('nova:en:')) {
        entry.nova = readScalarProperty(line, 'nova:en:')
      } else if (line.startsWith('from_palm_oil:en:')) {
        entry.fromPalmOil = readScalarProperty(line, 'from_palm_oil:en:')
      } else if (line.startsWith('vegan:en:')) {
        entry.vegan = readScalarProperty(line, 'vegan:en:')
      } else if (line.startsWith('vegetarian:en:')) {
        entry.vegetarian = readScalarProperty(line, 'vegetarian:en:')
      } else if (line.startsWith('description:en:')) {
        entry.description = readScalarProperty(line, 'description:en:')
      } else if (line.startsWith('wikipedia:en:')) {
        entry.wikipediaUrl = readScalarProperty(line, 'wikipedia:en:')
      } else if (line.startsWith('wikidata:en:')) {
        entry.wikidataId = readScalarProperty(line, 'wikidata:en:')
      } else if (line.startsWith('e_number:en:')) {
        const eNumber = readScalarProperty(line, 'e_number:en:')
        if (eNumber) {
          entry.codes.push(`E${eNumber.toUpperCase()}`)
        }
      }
    }

    entries.push(compactEntry(entry))
  }

  return dedupeEntries(entries)
}

function splitBlocks(text) {
  return text
    .replace(/\r\n/g, '\n')
    .split(/\n\s*\n/g)
}

function stripComment(line) {
  const trimmed = line.trim()
  if (trimmed.startsWith('#')) {
    return ''
  }
  return line
}

function readListProperty(line, prefix) {
  return line
    .slice(prefix.length)
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean)
}

function readScalarProperty(line, prefix) {
  const value = line.slice(prefix.length).trim()
  return value || null
}

function readParents(line) {
  return line
    .slice(1)
    .split(',')
    .map((value) => canonicalTaxonomyReference(value.trim()))
    .filter(Boolean)
}

function canonicalId(name, kind) {
  if (kind === 'additives') {
    const code = normalizeAdditiveCode(name)
    return code ? `en:${code.toLowerCase()}` : `en:${slug(name)}`
  }
  return `en:${slug(name)}`
}

function canonicalTaxonomyReference(value) {
  if (!value) {
    return null
  }
  if (/^[a-z]{2,3}:/i.test(value)) {
    const [prefix, rest] = value.split(/:(.+)/)
    const additiveCode = normalizeAdditiveCode(rest)
    return `${prefix.toLowerCase()}:${(additiveCode || slug(rest)).toLowerCase()}`
  }
  const additiveCode = normalizeAdditiveCode(value)
  return `en:${(additiveCode || slug(value)).toLowerCase()}`
}

function normalizeAdditiveCode(value) {
  const compact = value.replace(/\s+/g, '').toLowerCase()
  const match = compact.match(/^e(\d{3,4}[a-z]?(?:\([ivx]+\))?)$/i)
  if (match) {
    return `e${match[1]}`
  }
  return null
}

function slug(value) {
  return value
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/&/g, ' and ')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
}

function extractCodes(names, lines) {
  const values = [...names]
  for (const line of lines) {
    if (line.startsWith('e_number:en:')) {
      values.push(`E${readScalarProperty(line, 'e_number:en:')}`)
    }
  }

  return values
    .map((value) => normalizeAdditiveCode(value))
    .filter(Boolean)
    .map((code) => code.toUpperCase())
}

function compactEntry(entry) {
  return {
    id: entry.id,
    taxonomy: entry.taxonomy,
    names: uniqueSorted(entry.names),
    aliases: uniqueSorted(entry.aliases.filter((alias) => !entry.names.includes(alias))),
    codes: uniqueSorted(entry.codes),
    parents: uniqueSorted(entry.parents),
    classes: uniqueSorted(entry.classes),
    allergens: uniqueSorted(entry.allergens),
    nova: entry.nova,
    fromPalmOil: entry.fromPalmOil,
    vegan: entry.vegan,
    vegetarian: entry.vegetarian,
    description: entry.description,
    wikipediaUrl: entry.wikipediaUrl,
    wikidataId: entry.wikidataId,
  }
}

function uniqueSorted(values) {
  return [...new Set(values.filter(Boolean))].sort((a, b) => a.localeCompare(b))
}

function dedupeEntries(entries) {
  const byId = new Map()
  for (const entry of entries) {
    const existing = byId.get(entry.id)
    if (!existing) {
      byId.set(entry.id, entry)
      continue
    }

    existing.names = uniqueSorted([...existing.names, ...entry.names])
    existing.aliases = uniqueSorted([...existing.aliases, ...entry.aliases])
    existing.codes = uniqueSorted([...existing.codes, ...entry.codes])
    existing.parents = uniqueSorted([...existing.parents, ...entry.parents])
    existing.classes = uniqueSorted([...existing.classes, ...entry.classes])
    existing.allergens = uniqueSorted([...existing.allergens, ...entry.allergens])
    existing.nova ||= entry.nova
    existing.fromPalmOil ||= entry.fromPalmOil
    existing.vegan ||= entry.vegan
    existing.vegetarian ||= entry.vegetarian
    existing.description ||= entry.description
    existing.wikipediaUrl ||= entry.wikipediaUrl
    existing.wikidataId ||= entry.wikidataId
  }
  return [...byId.values()].sort((a, b) => a.id.localeCompare(b.id))
}

async function download(url) {
  const response = await fetch(url, {
    headers: {
      'User-Agent': 'NutriTrust taxonomy importer',
    },
  })
  if (!response.ok) {
    throw new Error(`Unable to download ${url}: ${response.status} ${response.statusText}`)
  }
  return response.text()
}

async function main() {
  await mkdir(OUTPUT_DIR, { recursive: true })

  for (const [kind, url] of Object.entries(TAXONOMIES)) {
    const text = await download(url)
    const entries = parseTaxonomy(text, kind)
    const outputPath = path.join(OUTPUT_DIR, `${kind}.json`)
    await writeFile(outputPath, `${JSON.stringify(entries, null, 2)}\n`, 'utf8')
    process.stdout.write(`${kind}: wrote ${entries.length} entries to ${outputPath}\n`)
  }
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  main().catch((error) => {
    console.error(error)
    process.exitCode = 1
  })
}
