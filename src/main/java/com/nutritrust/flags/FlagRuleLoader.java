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
import java.util.List;

@Component
public class FlagRuleLoader {

    private static final String RULE_RESOURCE_PATTERN = "classpath:/flag-rules/*.json";

    private final List<FlagRule> rules;

    @Autowired
    public FlagRuleLoader(ObjectMapper objectMapper) {
        this(objectMapper, new PathMatchingResourcePatternResolver());
    }

    FlagRuleLoader(ObjectMapper objectMapper, ResourcePatternResolver resourcePatternResolver) {
        this.rules = loadRules(objectMapper, resourcePatternResolver);
    }

    public List<FlagRule> rules() {
        return rules;
    }

    private List<FlagRule> loadRules(ObjectMapper objectMapper, ResourcePatternResolver resourcePatternResolver) {
        try {
            Resource[] resources = resourcePatternResolver.getResources(RULE_RESOURCE_PATTERN);
            Arrays.sort(resources, Comparator.comparing(Resource::getFilename, Comparator.nullsLast(String::compareTo)));

            List<FlagRule> loadedRules = new ArrayList<>();
            for (Resource resource : resources) {
                loadedRules.addAll(objectMapper.readValue(resource.getInputStream(), new TypeReference<List<FlagRule>>() {
                }));
            }
            return List.copyOf(loadedRules);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load flag rule resources", ex);
        }
    }
}
