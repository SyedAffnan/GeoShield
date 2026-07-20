package com.geoshield.identity.dto;

public record EmergencyContactResponse(Long contactId, String contactName, String contactPhone, boolean isPrimary) { }
