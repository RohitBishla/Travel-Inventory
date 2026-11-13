package com.navan.travel;

import java.util.List;

import org.springdoc.core.configuration.SpringDocRequiredModule;
import org.springdoc.core.configuration.SpringDocSealedClassModule;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;

import tools.jackson.databind.JsonNode;

@Configuration
class OpenApiConfig {

    private static final List<String> METADATA_SCHEMAS =
            List.of("FlightMetadata", "HotelMetadata", "RailMetadata", "NavanFeeMetadata");

    static {
        SpringDocUtils.getConfig().replaceWithSchema(JsonNode.class, anyMetadataSchema());
    }

    @Bean
    OpenAPI travelInvoiceApi() {
        return new OpenAPI().info(new Info()
                .title("Travel transactions and invoices")
                .version("v1")
                .description("Stores flight, hotel, rail and Navan fee transactions and issues invoices as frozen snapshots."));
    }

    @Bean
    ModelResolver snakeCaseModelResolver(ObjectMapperProvider springdocMappers) {
        var schemaMapper = springdocMappers.jsonMapper().copy()
                .registerModules(new SpringDocRequiredModule(), new SpringDocSealedClassModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return new ModelResolver(schemaMapper).openapi31(true);
    }

    private static Schema<Object> anyMetadataSchema() {
        ComposedSchema schema = new ComposedSchema();
        METADATA_SCHEMAS.forEach(name -> schema.addOneOfItem(new Schema<>().$ref("#/components/schemas/" + name)));
        schema.description("Type-specific fields; the shape depends on the transaction type.");
        return schema;
    }
}
