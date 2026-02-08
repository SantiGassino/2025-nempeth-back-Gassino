package com.nempeth.korven.rest.dto;

import java.util.UUID;

public record TableSimpleResponse(
    UUID id,
    String tableCode,
    Integer capacity
) {}
