package com.okoshko.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.okoshko.dto.SubmitRequestDto;
import com.okoshko.entity.CreatedRequestEntity;
import com.okoshko.repository.RequestRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RequestServiceTest {
	private final RequestRepository repository = mock(RequestRepository.class);
	private final RequestService service = new RequestService(repository);

	@Test
	void submitsAUserApplicationForOneExistingGroupAndRequiresConsent() {
		UUID key = UUID.randomUUID();
		OffsetDateTime submittedAt = OffsetDateTime.parse("2026-11-08T10:00:00Z");
		when(repository.findApplicantUserId(123L)).thenReturn(Optional.of(55L));
		when(repository.groupExists(7L)).thenReturn(true);
		when(repository.create(eq(55L), any(), eq(key), any(), eq("consent-demo-v1")))
				.thenReturn(new CreatedRequestEntity(91L, "pending", submittedAt, 7L, false));

		var response = service.submit(123L,
				new SubmitRequestDto(7L, "Аня", "Иванова", "+7 900 000-00-00", true), key);

		assertEquals(91L, response.requestId());
		assertEquals(7L, response.groupId());
		assertEquals("pending", response.status());
		verify(repository).create(eq(55L), any(), eq(key), any(), eq("consent-demo-v1"));
	}

	@Test
	void rejectsApplicationWithoutConsent() {
		assertThrows(ResponseStatusException.class, () -> service.submit(123L,
				new SubmitRequestDto(7L, "Аня", "Иванова", "+7 900 000-00-00", false), UUID.randomUUID()));
	}

	@Test
	void rejectsMissingGroup() {
		when(repository.findApplicantUserId(123L)).thenReturn(Optional.of(55L));
		when(repository.groupExists(7L)).thenReturn(false);

		assertThrows(ResponseStatusException.class, () -> service.submit(123L,
				new SubmitRequestDto(7L, "Аня", "Иванова", "+7 900 000-00-00", true), UUID.randomUUID()));
	}
}
