package com.nutritrust.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nutritrust.dto.NutritionDto;
import com.nutritrust.dto.ProductLookupResponse;
import com.nutritrust.exception.InvalidBarcodeException;
import com.nutritrust.exception.OpenFoodFactsApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class ProductLookupService {

    private static final Pattern BARCODE_PATTERN = Pattern.compile("^\\d{6,18}$");
    private static final String DATA_SOURCE = "Open Food Facts";
    private static final String SUCCESS_MESSAGE = "Product details fetched successfully";

    private final RestClient restClient;

    public ProductLookupService(@Value("${openfoodfacts.api.base-url}") String openFoodFactsBaseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(openFoodFactsBaseUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, "NutriTrustAI/0.1 (contact: local-development)")
                .build();
    }

    public ProductLookupResponse lookupByBarcode(String barcode) {
        validateBarcode(barcode);

        JsonNode root = fetchProduct(barcode);
        if (!isProductFound(root)) {
            return ProductLookupResponse.notFound(barcode);
        }

        JsonNode product = root.path("product");
        JsonNode nutriments = product.path("nutriments");

        return new ProductLookupResponse(
                true,
                textOrDefault(root.path("code"), barcode),
                textOrNull(product.path("product_name")),
                firstCsvValue(product.path("brands")),
                firstCsvValue(product.path("categories")),
                textOrNull(product.path("ingredients_text")),
                textOrNull(product.path("allergens")),
                stringList(product.path("additives_tags")),
                textOrNull(product.path("image_url")),
                new NutritionDto(
                        doubleOrNull(nutriments.path("energy-kcal_100g")),
                        doubleOrNull(nutriments.path("sugars_100g")),
                        doubleOrNull(nutriments.path("fat_100g")),
                        doubleOrNull(nutriments.path("saturated-fat_100g")),
                        doubleOrNull(nutriments.path("proteins_100g")),
                        doubleOrNull(nutriments.path("salt_100g")),
                        doubleOrNull(nutriments.path("sodium_100g")),
                        doubleOrNull(nutriments.path("fiber_100g"))
                ),
                DATA_SOURCE,
                SUCCESS_MESSAGE
        );
    }

    public JsonNode lookupRawByBarcode(String barcode) {
        validateBarcode(barcode);
        return fetchProduct(barcode);
    }

    private void validateBarcode(String barcode) {
        if (barcode == null || !BARCODE_PATTERN.matcher(barcode).matches()) {
            throw new InvalidBarcodeException("Invalid barcode. Barcode must contain 6 to 18 digits only.");
        }
    }

    private JsonNode fetchProduct(String barcode) {
        try {
            ResponseEntity<JsonNode> response = restClient.get()
                    .uri("/api/v2/product/{barcode}.json", barcode)
                    .retrieve()
                    .toEntity(JsonNode.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new OpenFoodFactsApiException("Open Food Facts returned an empty or invalid response");
            }

            return response.getBody();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return null;
            }
            throw new OpenFoodFactsApiException("Open Food Facts API error while fetching product details", ex);
        } catch (RestClientException ex) {
            throw new OpenFoodFactsApiException("Unable to reach Open Food Facts API", ex);
        }
    }

    private boolean isProductFound(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return false;
        }
        return root.path("status").asInt(0) == 1 && !root.path("product").isMissingNode() && !root.path("product").isNull();
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private String textOrDefault(JsonNode node, String defaultValue) {
        String value = textOrNull(node);
        return value == null ? defaultValue : value;
    }

    private String firstCsvValue(JsonNode node) {
        String value = textOrNull(node);
        if (value == null) {
            return null;
        }

        String firstValue = value.split(",", 2)[0].trim();
        return firstValue.isBlank() ? null : firstValue;
    }

    private Double doubleOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        String value = node.asText(null);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            String value = textOrNull(item);
            if (value != null) {
                values.add(cleanTag(value));
            }
        });
        return values;
    }

    private String cleanTag(String value) {
        int prefixSeparator = value.indexOf(':');
        if (prefixSeparator >= 0 && prefixSeparator < value.length() - 1) {
            return value.substring(prefixSeparator + 1);
        }
        return value;
    }
}
