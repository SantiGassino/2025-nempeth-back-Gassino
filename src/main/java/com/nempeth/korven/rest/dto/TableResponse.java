package com.nempeth.korven.rest.dto;

import com.nempeth.korven.constants.TableStatus;

import java.util.UUID;

public record TableResponse(
    UUID id,
    String tableCode,
    Integer capacity,
    String sector,
    TableStatus status
) {}
