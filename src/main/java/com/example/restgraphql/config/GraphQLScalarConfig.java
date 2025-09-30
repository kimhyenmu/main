package com.example.restgraphql.config;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Configuration
public class GraphQLScalarConfig {
    
    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder
                .scalar(GraphQLScalarType.newScalar()
                        .name("DateTime")
                        .description("LocalDateTime scalar")
                        .coercing(new Coercing<LocalDateTime, String>() {
                            @Override
                            public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
                                if (dataFetcherResult instanceof LocalDateTime) {
                                    return ((LocalDateTime) dataFetcherResult).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                                }
                                throw new CoercingSerializeException("Expected LocalDateTime");
                            }
                            
                            @Override
                            public LocalDateTime parseValue(Object input) throws CoercingParseValueException {
                                if (input instanceof String) {
                                    return LocalDateTime.parse((String) input, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                                }
                                throw new CoercingParseValueException("Expected String");
                            }
                            
                            @Override
                            public LocalDateTime parseLiteral(Object input) throws CoercingParseLiteralException {
                                if (input instanceof StringValue) {
                                    return LocalDateTime.parse(((StringValue) input).getValue(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                                }
                                throw new CoercingParseLiteralException("Expected StringValue");
                            }
                        })
                        .build())
                .scalar(GraphQLScalarType.newScalar()
                        .name("Long")
                        .description("Long scalar")
                        .coercing(new Coercing<Long, Long>() {
                            @Override
                            public Long serialize(Object dataFetcherResult) throws CoercingSerializeException {
                                if (dataFetcherResult instanceof Long) {
                                    return (Long) dataFetcherResult;
                                }
                                if (dataFetcherResult instanceof Integer) {
                                    return ((Integer) dataFetcherResult).longValue();
                                }
                                throw new CoercingSerializeException("Expected Long or Integer");
                            }
                            
                            @Override
                            public Long parseValue(Object input) throws CoercingParseValueException {
                                if (input instanceof Long) {
                                    return (Long) input;
                                }
                                if (input instanceof Integer) {
                                    return ((Integer) input).longValue();
                                }
                                throw new CoercingParseValueException("Expected Long or Integer");
                            }
                            
                            @Override
                            public Long parseLiteral(Object input) throws CoercingParseLiteralException {
                                if (input instanceof graphql.language.IntValue) {
                                    return ((graphql.language.IntValue) input).getValue().longValue();
                                }
                                throw new CoercingParseLiteralException("Expected IntValue");
                            }
                        })
                        .build());
    }
}