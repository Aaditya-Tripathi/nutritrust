import assert from 'node:assert/strict'
import test from 'node:test'
import { parseTaxonomy } from './taxonomy-importer.mjs'

test('parses additive entries with codes and classes', () => {
  const entries = parseTaxonomy(`
en: E330, Citric acid
additives_classes:en: en:acidity-regulator, en:antioxidant
e_number:en: 330
vegan:en: yes
description:en: Citric acid is used in foods.
wikipedia:en: https://en.wikipedia.org/wiki/Citric_acid
wikidata:en: Q159683
`, 'additives')

  assert.equal(entries.length, 1)
  assert.equal(entries[0].id, 'en:e330')
  assert.deepEqual(entries[0].codes, ['E330'])
  assert.deepEqual(entries[0].classes, ['en:acidity-regulator', 'en:antioxidant'])
  assert.equal(entries[0].vegan, 'yes')
  assert.equal(entries[0].description, 'Citric acid is used in foods.')
  assert.equal(entries[0].wikipediaUrl, 'https://en.wikipedia.org/wiki/Citric_acid')
  assert.equal(entries[0].wikidataId, 'Q159683')
})

test('parses allergen aliases and parent references', () => {
  const entries = parseTaxonomy(`
en: gluten, cereals containing gluten, wheat

< en:gluten
en: wheat gluten, wheat protein
`, 'allergens')

  assert.equal(entries[0].id, 'en:gluten')
  assert.ok(entries[0].aliases.includes('wheat'))
  assert.equal(entries[1].id, 'en:wheat-gluten')
  assert.deepEqual(entries[1].parents, ['en:gluten'])
})

test('parses ingredient properties used by flags', () => {
  const entries = parseTaxonomy(`
< en:vegetable-oils
en: palm oil, palm fat
allergens:en: en:nuts
nova:en: 4
from_palm_oil:en: yes
vegetarian:en: yes
`, 'ingredients')

  assert.equal(entries[0].id, 'en:palm-oil')
  assert.deepEqual(entries[0].parents, ['en:vegetable-oils'])
  assert.deepEqual(entries[0].allergens, ['en:nuts'])
  assert.equal(entries[0].nova, '4')
  assert.equal(entries[0].fromPalmOil, 'yes')
  assert.equal(entries[0].vegetarian, 'yes')
})
