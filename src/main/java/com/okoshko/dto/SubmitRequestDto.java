package com.okoshko.dto;

public record SubmitRequestDto(
		Long groupId,
		String childFirstName,
		String childLastName,
		String parentPhone,
		Boolean personalDataConsent) {
}
