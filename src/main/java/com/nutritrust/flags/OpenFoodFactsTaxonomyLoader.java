package com.nutritrust.flags;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class OpenFoodFactsTaxonomyLoader {

    private static final String TAXONOMY_RESOURCE_PATTERN = "classpath:/openfoodfacts-taxonomies/*.json";

    private final Map<String, TaxonomyEntry> entriesById;
    private final List<TaxonomyEntry> entries;

    @Autowired
    public OpenFoodFactsTaxonomyLoader(ObjectMapper objectMapper) {
        this(objectMapper, new PathMatchingResourcePatternResolver());
    }

    OpenFoodFactsTaxonomyLoader(ObjectMapper objectMapper, ResourcePatternResolver resourcePatternResolver) {
        this.entries = loadTaxonomies(objectMapper, resourcePatternResolver);
        this.entriesById = indexEntries(entries);
    }

    public TaxonomyEntry entry(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return entriesById.get(normalizeId(id));
    }

    public List<TaxonomyEntry> entries(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<TaxonomyEntry> entries = new ArrayList<>();
        for (String id : ids) {
            TaxonomyEntry entry = entry(id);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    public Map<String, TaxonomyEntry> entriesById() {
        return entriesById;
    }

    public List<TaxonomyEntry> allEntries() {
        return entries;
    }

    private List<TaxonomyEntry> loadTaxonomies(ObjectMapper objectMapper, ResourcePatternResolver resourcePatternResolver) {
        try {
            Resource[] resources = resourcePatternResolver.getResources(TAXONOMY_RESOURCE_PATTERN);
            Arrays.sort(resources, Comparator.comparing(Resource::getFilename, Comparator.nullsLast(String::compareTo)));

            List<TaxonomyEntry> entries = new ArrayList<>();
            for (Resource resource : resources) {
                List<TaxonomyEntry> loadedEntries = objectMapper.readValue(resource.getInputStream(), new TypeReference<List<TaxonomyEntry>>() {
                });
                entries.addAll(loadedEntries);
            }
            return List.copyOf(entries);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load Open Food Facts taxonomy resources", ex);
        }
    }

    private Map<String, TaxonomyEntry> indexEntries(List<TaxonomyEntry> entries) {
        Map<String, TaxonomyEntry> indexed = new LinkedHashMap<>();
        for (TaxonomyEntry entry : entries) {
            if (entry.id() != null && !entry.id().isBlank()) {
                indexed.putIfAbsent(normalizeId(entry.id()), entry);
            }
        }
        return Map.copyOf(indexed);
    }

    private String normalizeId(String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }
}
